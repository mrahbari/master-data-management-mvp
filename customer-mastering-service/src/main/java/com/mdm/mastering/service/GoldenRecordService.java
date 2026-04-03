/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import com.mdm.mastering.dto.CustomerMasteredEvent;
import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.entity.CustomerGoldenEntity;
import com.mdm.mastering.exception.ClassifiedException;
import com.mdm.mastering.exception.ErrorType;
import com.mdm.mastering.health.MdmProcessingHealthIndicator;
import com.mdm.mastering.metrics.MdmSliMetrics;
import com.mdm.mastering.repository.CustomerGoldenRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core MDM service for golden record management.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Deduplication using nationalId as the canonical unique identifier</li>
 *   <li>Golden record creation/update with merge strategy</li>
 *   <li>SLI metrics recording and health indicator updates</li>
 * </ul>
 */
@Service
public class GoldenRecordService {

  private static final Logger log = LoggerFactory.getLogger(GoldenRecordService.class);

  private final CustomerGoldenRepository goldenRepository;
  private final CustomerMasteredEventProducer eventProducer;
  private final MdmProcessingHealthIndicator healthIndicator;
  private final MdmSliMetrics metrics;

  public GoldenRecordService(
      CustomerGoldenRepository goldenRepository,
      CustomerMasteredEventProducer eventProducer,
      MdmProcessingHealthIndicator healthIndicator,
      MdmSliMetrics metrics) {
    this.goldenRepository = goldenRepository;
    this.eventProducer = eventProducer;
    this.healthIndicator = healthIndicator;
    this.metrics = metrics;
  }

  /**
   * Processes a customer event for deduplication and golden record management.
   *
   * @param event the raw customer event from Kafka
   */
  @Transactional
  public void processCustomerEvent(CustomerRawEvent event) {
    metrics.recordProcessing(() -> processEventInternal(event));
  }

  private void processEventInternal(CustomerRawEvent event) {
    String nationalId = normalizeNationalId(event.getNationalId());

    if (nationalId == null) {
      throw new ClassifiedException(
          "Event has null or blank nationalId: eventId=" + event.getEventId(),
          ErrorType.BUSINESS);
    }

    try {
      var existing = goldenRepository.findByNationalId(nationalId);

      if (existing.isPresent()) {
        handleExistingCustomer(event, existing.get());
      } else {
        handleNewCustomer(event, nationalId);
      }
    } catch (DataIntegrityViolationException ex) {
      throw new ClassifiedException(
          "Data integrity violation for nationalId=" + maskNationalId(nationalId),
          ErrorType.PERMANENT, ex);
    } catch (Exception ex) {
      if (ex instanceof ClassifiedException) {
        throw ex;
      }
      throw new ClassifiedException(
          "Unexpected error processing event for nationalId=" + maskNationalId(nationalId),
          ErrorType.TRANSIENT, ex);
    }
  }

  private void handleExistingCustomer(CustomerRawEvent event, CustomerGoldenEntity existing) {
    log.info(
        "Duplicate detected: nationalId={}, eventId={}, goldenRecordId={}",
        maskNationalId(event.getNationalId()),
        event.getEventId(),
        existing.getId());

    metrics.recordDuplicate();
    healthIndicator.recordProcessing(true);

    CustomerGoldenEntity updated = mergeGoldenRecord(existing, event);
    goldenRepository.save(updated);

    metrics.recordGoldenRecordUpdated();
    publishMasteredEvent(updated, event, CustomerMasteredEvent.MasteringAction.UPDATED);
  }

  private void handleNewCustomer(CustomerRawEvent event, String nationalId) {
    log.info(
        "Creating new golden record: nationalId={}, eventId={}",
        maskNationalId(nationalId),
        event.getEventId());

    CustomerGoldenEntity newRecord = createGoldenRecord(event, nationalId);
    goldenRepository.save(newRecord);

    metrics.recordGoldenRecordCreated();
    healthIndicator.recordProcessing(false);
    publishMasteredEvent(newRecord, event, CustomerMasteredEvent.MasteringAction.CREATED);
  }

  private CustomerGoldenEntity createGoldenRecord(CustomerRawEvent event, String nationalId) {
    return CustomerGoldenEntity.builder()
        .id(UUID.randomUUID())
        .nationalId(nationalId)
        .name(event.getName())
        .email(event.getEmail())
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
   * <p>Merge strategy (MVP): Trust latest data for mutable fields, keep nationalId immutable.
   */
  private CustomerGoldenEntity mergeGoldenRecord(
      CustomerGoldenEntity existing, CustomerRawEvent event) {
    existing.setName(coalesce(event.getName(), existing.getName()));
    existing.setEmail(coalesce(event.getEmail(), existing.getEmail()));
    existing.setPhone(coalesce(event.getPhone(), existing.getPhone()));
    existing.setLastSourceSystem(event.getSourceSystem());
    existing.setUpdatedAt(Instant.now());
    existing.setVersion(existing.getVersion() + 1);

    return existing;
  }

  private void publishMasteredEvent(
      CustomerGoldenEntity golden,
      CustomerRawEvent rawEvent,
      CustomerMasteredEvent.MasteringAction action) {
    CustomerMasteredEvent masteredEvent =
        CustomerMasteredEvent.builder()
            .eventId(UUID.randomUUID())
            .goldenRecordId(golden.getId())
            .nationalId(golden.getNationalId())
            .name(golden.getName())
            .email(golden.getEmail())
            .phone(golden.getPhone())
            .action(action)
            .timestamp(Instant.now())
            .build();

    eventProducer.publish(masteredEvent);
  }

  private static String normalizeNationalId(String nationalId) {
    if (nationalId == null || nationalId.isBlank()) {
      return null;
    }
    return nationalId.trim().replaceAll("[^a-zA-Z0-9]", "");
  }

  private static String maskNationalId(String nationalId) {
    if (nationalId == null || nationalId.length() <= 4) {
      return "***";
    }
    return "***" + nationalId.substring(nationalId.length() - 4);
  }

  /** Coalesce helper: return value if not null, otherwise default. */
  private static <T> T coalesce(T value, T defaultValue) {
    return value != null ? value : defaultValue;
  }
}
