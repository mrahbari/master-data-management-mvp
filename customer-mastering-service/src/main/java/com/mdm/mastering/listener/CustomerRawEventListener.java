/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.listener;

import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.dto.dlq.DlqEvent;
import com.mdm.mastering.exception.ClassifiedException;
import com.mdm.mastering.exception.ErrorType;
import com.mdm.mastering.metrics.RetryAndDlqMetrics;
import com.mdm.mastering.repository.CustomerRawRepository;
import com.mdm.mastering.service.DlqMessageFormatter;
import com.mdm.mastering.service.DlqProducer;
import com.mdm.mastering.service.GoldenRecordService;
import com.mdm.mastering.util.SensitiveDataMasker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Kafka listener for customer raw events with retry and DLQ support.
 *
 * <p>Processing flow:
 *
 * <ol>
 *   <li>Check idempotency (skip if already processed by eventId)
 *   <li>Store raw event for audit trail
 *   <li>Process via GoldenRecordService (with Spring Retry for transient errors)
 *   <li>Acknowledge on success
 *   <li>Send to DLQ after all retries exhausted
 * </ol>
 */
@Component
public class CustomerRawEventListener {

  private static final Logger log = LoggerFactory.getLogger(CustomerRawEventListener.class);
  private static final Logger retryLog = LoggerFactory.getLogger("com.mdm.mastering.retry");
  private static final Logger dlqLog = LoggerFactory.getLogger("com.mdm.mastering.dlq");
  private static final Marker RETRY_MARKER = MarkerFactory.getMarker("RETRY");
  private static final Marker DLQ_MARKER = MarkerFactory.getMarker("DLQ");

  // ThreadLocal to carry Kafka offset into the recovery context
  private static final ThreadLocal<Long> CURRENT_OFFSET = new ThreadLocal<>();

  private final CustomerRawRepository rawRepository;
  private final GoldenRecordService goldenRecordService;
  private final DlqProducer dlqProducer;
  private final DlqMessageFormatter dlqMessageFormatter;
  private final RetryAndDlqMetrics retryAndDlqMetrics;
  private final ObjectMapper objectMapper;
  private final String originalTopic;

  // Metrics
  private final Counter processedEventsCounter;
  private final Counter failedEventsCounter;

  @Value("${kafka.retry.maxAttempts:3}")
  private int maxRetryAttempts;

  public CustomerRawEventListener(
      CustomerRawRepository rawRepository,
      GoldenRecordService goldenRecordService,
      DlqProducer dlqProducer,
      DlqMessageFormatter dlqMessageFormatter,
      RetryAndDlqMetrics retryAndDlqMetrics,
      MeterRegistry meterRegistry,
      ObjectMapper objectMapper,
      @Value("${kafka.topics.customer-raw:customer.raw}") String originalTopic) {
    this.rawRepository = rawRepository;
    this.goldenRecordService = goldenRecordService;
    this.dlqProducer = dlqProducer;
    this.dlqMessageFormatter = dlqMessageFormatter;
    this.retryAndDlqMetrics = retryAndDlqMetrics;
    this.objectMapper = objectMapper;
    this.originalTopic = originalTopic;

    this.processedEventsCounter =
        Counter.builder("processed_events_total")
            .description("Total number of customer raw events processed")
            .register(meterRegistry);

    this.failedEventsCounter =
        Counter.builder("failed_events_total")
            .description("Total number of customer raw events that failed processing")
            .register(meterRegistry);
  }

  @KafkaListener(
      topics = "${kafka.topics.customer-raw}",
      groupId = "${spring.kafka.consumer.group-id}")
  public void listen(CustomerRawEvent event, Acknowledgment ack, ConsumerRecord<?, ?> record) {
    log.info(
        "Received raw customer event: eventId={}, nationalId={}, source={}",
        event.getEventId(),
        SensitiveDataMasker.maskNationalId(event.getNationalId()),
        event.getSourceSystem());

    try {
      // Process with retry (includes atomic raw event storage)
      processWithRetry(event, record.offset());

      // Acknowledge after successful processing
      ack.acknowledge();
      processedEventsCounter.increment();
      retryAndDlqMetrics.recordEventProcessed();

      log.info("Successfully processed event: eventId={}", event.getEventId());

    } catch (Exception ex) {
      log.error(
          "Failed to process event: eventId={}, error={}", event.getEventId(), ex.getMessage(), ex);
      failedEventsCounter.increment();

      // Don't acknowledge - Kafka will redeliver
      throw ex;
    }
  }

  /**
   * Processes the event with Spring Retry for transient errors. Non-retryable errors are thrown
   * immediately without retry.
   */
  @Retryable(
      retryFor = {
        DeadlockLoserDataAccessException.class,
        QueryTimeoutException.class,
        ClassifiedException.class
      },
      maxAttemptsExpression = "${kafka.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${kafka.retry.initial-interval:1000}",
              multiplierExpression = "${kafka.retry.multiplier:2.0}",
              maxDelayExpression = "${kafka.retry.max-interval:10000}"))
  private void processWithRetry(CustomerRawEvent event, long offset) {
    retryAndDlqMetrics.recordEventProcessed();

    CURRENT_OFFSET.set(offset);
    try {
      // Store raw event (audit trail) - atomic insert to prevent duplicate processing
      int inserted =
          rawRepository.saveIfNotExists(
              UUID.randomUUID(),
              event.getEventId(),
              event.getNationalId(),
              event.getName(),
              event.getEmail(),
              event.getPhone(),
              event.getSourceSystem(),
              toJson(event),
              Instant.now());

      if (inserted == 0) {
        // Event was already processed - skip idempotently
        log.warn(
            "Event already processed (atomic idempotent skip): eventId={}", event.getEventId());
        retryAndDlqMetrics.recordEventProcessed();
        return;
      }

      // Process for golden record creation/update
      goldenRecordService.processCustomerEvent(event);

      logRetry(event, 0, null, true);
    } finally {
      CURRENT_OFFSET.remove();
    }
  }

  /**
   * Recovery handler called when all retry attempts are exhausted. Sends the event to the Dead
   * Letter Queue.
   */
  @Recover
  public void recover(Exception ex, CustomerRawEvent event) {
    long offset = CURRENT_OFFSET.get() != null ? CURRENT_OFFSET.get() : 0L;
    recoverWithOffset(ex, event, offset);
  }

  /** Recovery handler with Kafka offset context. */
  public void recoverWithOffset(Exception ex, CustomerRawEvent event, long offset) {
    ErrorType errorType = dlqMessageFormatter.classifyException(ex);
    int retryCount = maxRetryAttempts;

    retryAndDlqMetrics.recordDlqMessage(errorType);

    DlqEvent dlqEvent = dlqMessageFormatter.formatDlqEvent(event, ex, errorType, retryCount);

    dlqProducer.sendToDlq(event, dlqEvent, errorType, retryCount, originalTopic, offset);

    logDlq(event, ex, errorType, retryCount);
  }

  private void logRetry(CustomerRawEvent event, int attempt, Exception ex, boolean success) {
    if (ex != null) {
      retryLog.warn(
          RETRY_MARKER,
          "Retry attempt for event: eventId={}, attempt={}, error={}",
          event.getEventId(),
          attempt,
          ex.getMessage());
    } else if (success) {
      retryLog.info(RETRY_MARKER, "Event processed successfully: eventId={}", event.getEventId());
    }
  }

  private void logDlq(CustomerRawEvent event, Exception ex, ErrorType errorType, int retryCount) {
    dlqLog.error(
        DLQ_MARKER,
        "Message sent to DLQ: eventId={}, errorType={}, retryCount={}, error={}",
        event.getEventId(),
        errorType,
        retryCount,
        ex.getMessage());
  }

  private String toJson(CustomerRawEvent event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize event to JSON: eventId={}", event.getEventId(), e);
      return "{}";
    }
  }
}
