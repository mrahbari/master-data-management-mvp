/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mdm.ingestion.entity.OutboxEvent;
import com.mdm.ingestion.repository.OutboxEventRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

/**
 * Polls the transactional outbox for unpublished events and publishes them to Kafka.
 *
 * <p>This service implements the Transactional Outbox Pattern: 1. Events are written to the outbox
 * table in the same transaction as business data 2. This publisher polls the outbox and publishes
 * events to Kafka 3. After successful publish, the outbox entry is marked as processed 4. Failed
 * events are retried up to maxRetries, then sent to a DLQ topic
 *
 * <p>This guarantees that events are never lost even if Kafka is temporarily unavailable, and
 * eliminates the dual-write problem.
 */
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private static final String DLQ_TOPIC = "outbox.dlq";

  private final OutboxEventRepository outboxRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final MeterRegistry meterRegistry;

  @Value("${mdm.outbox.batch-size:100}")
  private int batchSize;

  @Value("${mdm.outbox.max-retries:3}")
  private int maxRetries;

  @Value("${mdm.outbox.publish-timeout-seconds:10}")
  private int publishTimeoutSeconds;

  @Value("${kafka.topics.customer-raw:customer.raw}")
  private String rawTopic;

  // Metrics
  private Counter publishedCounter;
  private Counter failedCounter;
  private Counter dlqCounter;

  /** Initialize metrics on construction. */
  @jakarta.annotation.PostConstruct
  public void initMetrics() {
    publishedCounter =
        Counter.builder("outbox_events_published_total")
            .description("Total outbox events published to Kafka")
            .register(meterRegistry);

    failedCounter =
        Counter.builder("outbox_events_failed_total")
            .description("Total outbox events that failed publishing")
            .register(meterRegistry);

    dlqCounter =
        Counter.builder("outbox_events_dlq_total")
            .description("Total outbox events sent to DLQ")
            .register(meterRegistry);

    Gauge.builder("outbox_events_pending", outboxRepository::countUnprocessed)
        .description("Number of pending outbox events")
        .register(meterRegistry);
  }

  /** Scheduled task that polls the outbox for unprocessed events and publishes them to Kafka. */
  @Scheduled(fixedDelayString = "${mdm.outbox.poll-interval:1000}")
  @Transactional
  public void publishPendingEvents() {
    List<OutboxEvent> events = outboxRepository.findUnprocessed(PageRequest.of(0, batchSize));

    if (events.isEmpty()) {
      return;
    }

    log.debug("Found {} pending outbox events to publish", events.size());

    for (OutboxEvent event : events) {
      try {
        publishEvent(event);
        event.markProcessed();
        outboxRepository.save(event);
        publishedCounter.increment();
        log.debug(
            "Published outbox event: id={}, aggregateId={}", event.getId(), event.getAggregateId());

      } catch (Exception ex) {
        handlePublishFailure(event, ex);
      }
    }
  }

  /**
   * Manually retry a specific outbox event by ID.
   *
   * @param eventId the outbox event ID to retry
   * @return true if the event was found and re-queued
   */
  @Transactional
  public boolean retryEvent(UUID eventId) {
    return outboxRepository
        .findUnprocessedById(eventId)
        .map(
            event -> {
              try {
                publishEvent(event);
                event.markProcessed();
                outboxRepository.save(event);
                publishedCounter.increment();
                log.info("Manually retried and published outbox event: id={}", eventId);
                return true;
              } catch (Exception ex) {
                log.warn("Manual retry failed for outbox event: id={}", eventId, ex);
                return false;
              }
            })
        .orElse(false);
  }

  /**
   * Force requeue a processed event for re-processing (admin operation).
   *
   * @param eventId the outbox event ID to requeue
   * @return true if the event was found and re-queued
   */
  @Transactional
  public boolean forceRequeueEvent(UUID eventId) {
    int updated = outboxRepository.forceRequeue(eventId);
    if (updated > 0) {
      log.info("Force requeued outbox event: id={}", eventId);
      return true;
    }
    log.warn(
        "Could not force requeue outbox event: id={} (not found or already unprocessed)", eventId);
    return false;
  }

  /** Get the current count of pending outbox events. */
  public long getPendingCount() {
    return outboxRepository.countUnprocessed();
  }

  private void publishEvent(OutboxEvent event) throws Exception {
    kafkaTemplate
        .send(rawTopic, event.getAggregateId(), event.getPayload())
        .get(publishTimeoutSeconds, TimeUnit.SECONDS);
  }

  private void handlePublishFailure(OutboxEvent event, Exception ex) {
    String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();

    if (event.isExhausted(maxRetries)) {
      log.error(
          "Outbox event failed after {} retries, sending to DLQ: id={}, lastError={}",
          maxRetries,
          event.getId(),
          errorMsg);
      sendToDlq(event, ex);
      event.markProcessed(); // Mark as processed so it stops being polled
      outboxRepository.save(event);
      dlqCounter.increment();
    } else {
      event.markForRetry(errorMsg);
      outboxRepository.save(event);
      failedCounter.increment();
      log.warn(
          "Outbox event publish failed, will retry: id={}, retryCount={}/{}",
          event.getId(),
          event.getRetryCount(),
          maxRetries);
    }
  }

  private void sendToDlq(OutboxEvent event, Exception ex) {
    try {
      String dlqPayload =
          String.format(
              "{\"outboxEventId\":\"%s\",\"aggregateType\":\"%s\",\"aggregateId\":\"%s\",\"eventType\":\"%s\",\"eventVersion\":\"%s\",\"originalPayload\":%s,\"error\":\"%s\",\"retryCount\":%d}",
              event.getId(),
              event.getAggregateType(),
              event.getAggregateId(),
              event.getEventType(),
              event.getEventVersion(),
              event.getPayload(),
              escapeJson(ex.getMessage()),
              event.getRetryCount());

      kafkaTemplate
          .send(DLQ_TOPIC, event.getAggregateId(), dlqPayload)
          .whenComplete(
              (result, sendEx) -> {
                if (sendEx != null) {
                  log.error("Failed to send outbox event to DLQ: id={}", event.getId(), sendEx);
                } else {
                  log.info("Outbox event sent to DLQ: id={}", event.getId());
                }
              });
    } catch (Exception ex2) {
      log.error("Critical: Failed to send outbox event to DLQ: id={}", event.getId(), ex2);
    }
  }

  private static String escapeJson(String value) {
    if (value == null) return "";
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
