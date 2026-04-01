/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.listener;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.entity.CustomerRawEntity;
import com.mdm.mastering.repository.CustomerRawRepository;
import com.mdm.mastering.service.GoldenRecordService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class CustomerRawEventListener {

  private static final Logger log = LoggerFactory.getLogger(CustomerRawEventListener.class);

  private final CustomerRawRepository rawRepository;
  private final GoldenRecordService goldenRecordService;

  // Metrics
  private final Counter processedEventsCounter;
  private final Counter failedEventsCounter;

  public CustomerRawEventListener(
      CustomerRawRepository rawRepository,
      GoldenRecordService goldenRecordService,
      MeterRegistry meterRegistry) {
    this.rawRepository = rawRepository;
    this.goldenRecordService = goldenRecordService;

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
  public void listen(CustomerRawEvent event, Acknowledgment ack) {
    log.info(
        "Received raw customer event: eventId={}, email={}, source={}",
        event.getEventId(),
        event.getEmail(),
        event.getSourceSystem());

    try {
      // Idempotency: skip if already processed
      if (rawRepository.existsByEventId(event.getEventId())) {
        log.warn("Event already processed (idempotent skip): eventId={}", event.getEventId());
        ack.acknowledge();
        return;
      }

      // Store raw event (audit trail)
      CustomerRawEntity rawEntity =
          CustomerRawEntity.builder()
              .id(java.util.UUID.randomUUID())
              .eventId(event.getEventId())
              .email(event.getEmail())
              .firstName(event.getFirstName())
              .lastName(event.getLastName())
              .phone(event.getPhone())
              .sourceSystem(event.getSourceSystem())
              .rawPayload(toJson(event))
              .createdAt(Instant.now())
              .build();

      rawRepository.save(rawEntity);

      // Process for golden record creation/update
      goldenRecordService.processCustomerEvent(event);

      // Acknowledge after successful processing
      ack.acknowledge();
      processedEventsCounter.increment();

      log.info("Successfully processed event: eventId={}", event.getEventId());

    } catch (Exception ex) {
      log.error(
          "Failed to process event: eventId={}, error={}", event.getEventId(), ex.getMessage(), ex);
      failedEventsCounter.increment();

      // Don't acknowledge - will be retried by Kafka
      // In production: send to DLQ after max retries
      throw ex;
    }
  }

  private String toJson(CustomerRawEvent event) {
    // Simple JSON serialization (use Jackson in production)
    return String.format(
        "{\"eventId\":\"%s\",\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\",\"phone\":\"%s\",\"sourceSystem\":\"%s\"}",
        event.getEventId(),
        event.getEmail(),
        event.getFirstName(),
        event.getLastName(),
        event.getPhone(),
        event.getSourceSystem());
  }
}
