/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mdm.mastering.dto.CustomerMasteredEvent;
import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.entity.CustomerGoldenEntity;
import com.mdm.mastering.health.MdmProcessingHealthIndicator;
import com.mdm.mastering.metrics.MdmSliMetrics;
import com.mdm.mastering.repository.CustomerGoldenRepository;

/**
 * Core MDM service for golden record management.
 *
 * <p>Responsibilities: - Deduplication using survivable matching rules - Golden record
 * creation/update - SLI metrics recording (via MdmSliMetrics) - Health indicator updates
 */
@Service
public class GoldenRecordService {

  private static final Logger log = LoggerFactory.getLogger(GoldenRecordService.class);

  private final CustomerGoldenRepository goldenRepository;
  private final DeduplicationService deduplicationService;
  private final CustomerMasteredEventProducer eventProducer;
  private final MdmProcessingHealthIndicator healthIndicator;
  private final MdmSliMetrics metrics;
  private final CustomerMatchingService matchingService;

  public GoldenRecordService(
      CustomerGoldenRepository goldenRepository,
      DeduplicationService deduplicationService,
      CustomerMasteredEventProducer eventProducer,
      MdmProcessingHealthIndicator healthIndicator,
      MdmSliMetrics metrics,
      CustomerMatchingService matchingService) {
    this.goldenRepository = goldenRepository;
    this.deduplicationService = deduplicationService;
    this.eventProducer = eventProducer;
    this.healthIndicator = healthIndicator;
    this.metrics = metrics;
    this.matchingService = matchingService;
  }

  /**
   * Process a customer event for deduplication and golden record management.
   *
   * @param event the raw customer event
   */
  @Transactional
  public void processCustomerEvent(CustomerRawEvent event) {
    metrics.recordProcessing(() -> processEventInternal(event));
  }

  /** Internal processing logic (wrapped in metrics recording). */
  private void processEventInternal(CustomerRawEvent event) {
    String normalizedEmail = deduplicationService.normalizeEmail(event.getEmail());

    if (normalizedEmail == null) {
      log.warn("Skipping event with null email: eventId={}", event.getEventId());
      return;
    }

    // Use survivable matching service for advanced duplicate detection
    CustomerMatchingService.MatchResult matchResult =
        matchingService.findMatch(
            event.getEmail(), event.getFirstName(), event.getLastName(), event.getPhone());

    if (matchResult.isDuplicate()) {
      // DUPLICATE DETECTED - Update existing golden record
      handleDuplicate(event, matchResult);
    } else if (matchResult.isPossibleDuplicate()) {
      // POSSIBLE DUPLICATE - Log for manual review, but create new record
      log.warn(
          "Possible duplicate detected (manual review recommended): eventId={}, email={}, score={}, rules={}",
          event.getEventId(),
          normalizedEmail,
          matchResult.getMatchScore(),
          matchResult.getMatchedRules());

      handleNewCustomer(event, normalizedEmail);
    } else {
      // NEW CUSTOMER - Create golden record
      handleNewCustomer(event, normalizedEmail);
    }
  }

  /** Handle duplicate customer event. */
  private void handleDuplicate(
      CustomerRawEvent event, CustomerMatchingService.MatchResult matchResult) {
    metrics.recordDuplicate();
    healthIndicator.recordProcessing(true);

    CustomerGoldenEntity existing = matchResult.getMatchedCustomer();
    log.info(
        "Duplicate detected: email={}, eventId={}, goldenRecordId={}, matchScore={}, rules={}",
        matchResult.getMatchedCustomer().getNormalizedEmail(),
        event.getEventId(),
        existing.getId(),
        matchResult.getMatchScore(),
        matchResult.getMatchedRules());

    CustomerGoldenEntity updated = mergeGoldenRecord(existing, event);
    goldenRepository.save(updated);

    metrics.recordGoldenRecordUpdated();
    publishMasteredEvent(updated, event, CustomerMasteredEvent.MasteringAction.UPDATED);
  }

  /** Handle new customer event. */
  private void handleNewCustomer(CustomerRawEvent event, String normalizedEmail) {
    log.info(
        "Creating new golden record: email={}, eventId={}", normalizedEmail, event.getEventId());

    CustomerGoldenEntity newRecord = createGoldenRecord(event, normalizedEmail);
    goldenRepository.save(newRecord);

    metrics.recordGoldenRecordCreated();
    healthIndicator.recordProcessing(false);
    publishMasteredEvent(newRecord, event, CustomerMasteredEvent.MasteringAction.CREATED);
  }

  /** Create a new golden record from raw event. */
  private CustomerGoldenEntity createGoldenRecord(CustomerRawEvent event, String normalizedEmail) {
    return CustomerGoldenEntity.builder()
        .id(UUID.randomUUID())
        .normalizedEmail(normalizedEmail)
        .email(event.getEmail())
        .firstName(event.getFirstName())
        .lastName(event.getLastName())
        .phone(event.getPhone())
        .confidenceScore((short) 100)
        .version(1L)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .lastSourceSystem(event.getSourceSystem())
        .build();
  }

  /**
   * Merge new event data into existing golden record.
   *
   * <p>Merge strategy (MVP): Trust latest data - Email: always update - Name fields: coalesce (keep
   * existing if new is null) - Phone: coalesce - Source system: always update (track last source)
   */
  private CustomerGoldenEntity mergeGoldenRecord(
      CustomerGoldenEntity existing, CustomerRawEvent event) {
    existing.setEmail(event.getEmail());
    existing.setFirstName(coalesce(event.getFirstName(), existing.getFirstName()));
    existing.setLastName(coalesce(event.getLastName(), existing.getLastName()));
    existing.setPhone(coalesce(event.getPhone(), existing.getPhone()));
    existing.setLastSourceSystem(event.getSourceSystem());
    existing.setUpdatedAt(Instant.now());
    existing.setVersion(existing.getVersion() + 1);

    return existing;
  }

  /** Coalesce helper: return value if not null, otherwise default. */
  private <T> T coalesce(T value, T defaultValue) {
    return value != null ? value : defaultValue;
  }

  /** Publish mastered event to Kafka. */
  private void publishMasteredEvent(
      CustomerGoldenEntity golden,
      CustomerRawEvent rawEvent,
      CustomerMasteredEvent.MasteringAction action) {
    CustomerMasteredEvent masteredEvent =
        CustomerMasteredEvent.builder()
            .eventId(UUID.randomUUID())
            .goldenRecordId(golden.getId())
            .email(golden.getEmail())
            .firstName(golden.getFirstName())
            .lastName(golden.getLastName())
            .phone(golden.getPhone())
            .action(action)
            .timestamp(Instant.now())
            .build();

    eventProducer.publish(masteredEvent);
  }
}
