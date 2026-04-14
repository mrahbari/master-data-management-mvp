/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.mastering.conflict.ConflictLogger;
import com.mdm.mastering.conflict.ConflictResolutionService;
import com.mdm.mastering.conflict.FieldConflict;
import com.mdm.mastering.conflict.FieldResolution;
import com.mdm.mastering.dto.CustomerMasteredEvent;
import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.entity.CustomerGoldenEntity;
import com.mdm.mastering.exception.ClassifiedException;
import com.mdm.mastering.exception.ErrorType;
import com.mdm.mastering.health.MdmProcessingHealthIndicator;
import com.mdm.mastering.metrics.MdmSliMetrics;
import com.mdm.mastering.repository.CustomerGoldenRepository;
import com.mdm.mastering.util.SensitiveDataMasker;

/**
 * Core MDM service for golden record management.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Deduplication using nationalId as the canonical unique identifier
 *   <li>Golden record creation/update with configurable conflict resolution strategies
 *   <li>SLI metrics recording and health indicator updates
 * </ul>
 */
@Service
public class GoldenRecordService {

  private static final Logger log = LoggerFactory.getLogger(GoldenRecordService.class);

  private final CustomerGoldenRepository goldenRepository;
  private final CustomerMasteredEventProducer eventProducer;
  private final MdmProcessingHealthIndicator healthIndicator;
  private final MdmSliMetrics metrics;
  private final ConflictResolutionService conflictResolutionService;
  private final ConflictLogger conflictLogger;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public GoldenRecordService(
      CustomerGoldenRepository goldenRepository,
      CustomerMasteredEventProducer eventProducer,
      MdmProcessingHealthIndicator healthIndicator,
      MdmSliMetrics metrics,
      ConflictResolutionService conflictResolutionService,
      ConflictLogger conflictLogger,
      ObjectMapper objectMapper,
      Clock clock) {
    this.goldenRepository = goldenRepository;
    this.eventProducer = eventProducer;
    this.healthIndicator = healthIndicator;
    this.metrics = metrics;
    this.conflictResolutionService = conflictResolutionService;
    this.conflictLogger = conflictLogger;
    this.objectMapper = objectMapper;
    this.clock = clock;
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
          "Event has null or blank nationalId: eventId=" + event.getEventId(), ErrorType.BUSINESS);
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
          "Data integrity violation for nationalId="
              + SensitiveDataMasker.maskNationalId(nationalId),
          ErrorType.PERMANENT,
          ex);
    } catch (Exception ex) {
      if (ex instanceof ClassifiedException) {
        throw ex;
      }
      throw new ClassifiedException(
          "Unexpected error processing event for nationalId="
              + SensitiveDataMasker.maskNationalId(nationalId),
          ErrorType.TRANSIENT,
          ex);
    }
  }

  private void handleExistingCustomer(CustomerRawEvent event, CustomerGoldenEntity existing) {
    log.info(
        "Duplicate detected: nationalId={}, eventId={}, goldenRecordId={}",
        SensitiveDataMasker.maskNationalId(event.getNationalId()),
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
        SensitiveDataMasker.maskNationalId(nationalId),
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
        .createdAt(Instant.now(clock))
        .updatedAt(Instant.now(clock))
        .lastSourceSystem(event.getSourceSystem())
        .build();
  }

  /**
   * Merge new event data into existing golden record using configurable conflict resolution.
   *
   * <p>Each field is resolved according to the configured strategy in application.yml. Conflicts
   * are logged with full reasoning.
   */
  private CustomerGoldenEntity mergeGoldenRecord(
      CustomerGoldenEntity existing, CustomerRawEvent event) {
    String nationalId = existing.getNationalId();
    Instant eventTimestamp =
        event.getTimestamp() != null ? event.getTimestamp() : Instant.now(clock);
    Instant existingTimestamp = existing.getUpdatedAt();

    // Resolve each field using the conflict resolution service
    FieldResolution nameResolution =
        resolveField(
            "name",
            existing.getName(),
            event.getName(),
            existingTimestamp,
            eventTimestamp,
            existing.getLastSourceSystem(),
            event.getSourceSystem(),
            nationalId);

    FieldResolution emailResolution =
        resolveField(
            "email",
            existing.getEmail(),
            event.getEmail(),
            existingTimestamp,
            eventTimestamp,
            existing.getLastSourceSystem(),
            event.getSourceSystem(),
            nationalId);

    FieldResolution phoneResolution =
        resolvePhoneField(
            existing.getPhone(),
            event.getPhone(),
            existingTimestamp,
            eventTimestamp,
            existing.getLastSourceSystem(),
            event.getSourceSystem(),
            nationalId);

    // Apply resolved values
    existing.setName((String) nameResolution.resolvedValue());
    existing.setEmail((String) emailResolution.resolvedValue());
    existing.setPhone(serializePhone(phoneResolution));
    existing.setLastSourceSystem(event.getSourceSystem());
    existing.setUpdatedAt(Instant.now(clock));
    existing.setVersion(existing.getVersion() + 1);

    return existing;
  }

  /** Resolves a single field conflict and logs the result. */
  private FieldResolution resolveField(
      String fieldName,
      Object currentValue,
      Object incomingValue,
      Instant currentTimestamp,
      Instant incomingTimestamp,
      String currentSource,
      String incomingSource,
      String nationalId) {

    if (currentValue == null && incomingValue == null) {
      return FieldResolution.unchanged(null, "Both values are null");
    }

    if (currentValue == null) {
      return FieldResolution.changed(incomingValue, "Current value is null, using incoming");
    }

    if (incomingValue == null) {
      return FieldResolution.unchanged(currentValue, "Incoming value is null, keeping current");
    }

    if (currentValue.equals(incomingValue)) {
      return FieldResolution.unchanged(currentValue, "Values are identical");
    }

    // Real conflict - resolve it
    FieldConflict conflict =
        new FieldConflict(
            fieldName,
            currentValue,
            incomingValue,
            currentTimestamp,
            incomingTimestamp,
            currentSource,
            incomingSource);

    FieldResolution resolution = conflictResolutionService.resolve(conflict);
    conflictLogger.logConflict(
        nationalId,
        conflict,
        resolution,
        conflictResolutionService.getConfigForField(fieldName).strategy().name());

    return resolution;
  }

  /** Resolves phone field with special handling for multi-value arrays. */
  private FieldResolution resolvePhoneField(
      String existingPhone,
      String incomingPhone,
      Instant existingTimestamp,
      Instant incomingTimestamp,
      String existingSource,
      String incomingSource,
      String nationalId) {

    List<String> existingPhones = parsePhoneList(existingPhone);
    List<String> incomingPhones = parsePhoneList(incomingPhone);

    if (existingPhones.isEmpty() && incomingPhones.isEmpty()) {
      return FieldResolution.unchanged(null, "Both phone values are null/empty");
    }

    if (existingPhones.isEmpty()) {
      return FieldResolution.changed(incomingPhones, "No existing phones, using incoming");
    }

    if (incomingPhones.isEmpty()) {
      return FieldResolution.unchanged(existingPhones, "No incoming phones, keeping existing");
    }

    if (existingPhones.equals(incomingPhones)) {
      return FieldResolution.unchanged(existingPhones, "Phone lists are identical");
    }

    // Real conflict - resolve using configured strategy
    FieldConflict conflict =
        new FieldConflict(
            "phone",
            existingPhones,
            incomingPhones,
            existingTimestamp,
            incomingTimestamp,
            existingSource,
            incomingSource);

    FieldResolution resolution = conflictResolutionService.resolve(conflict);
    conflictLogger.logConflict(
        nationalId,
        conflict,
        resolution,
        conflictResolutionService.getConfigForField("phone").strategy().name());

    return resolution;
  }

  @SuppressWarnings("unchecked")
  private List<String> parsePhoneList(String phoneValue) {
    if (phoneValue == null || phoneValue.isBlank()) {
      return new ArrayList<>();
    }

    // Try to parse as JSON array first
    if (phoneValue.trim().startsWith("[")) {
      try {
        return objectMapper.readValue(phoneValue, new TypeReference<List<String>>() {});
      } catch (JsonProcessingException e) {
        // Fall through to treat as single value
      }
    }

    // Single phone value
    List<String> phones = new ArrayList<>();
    phones.add(phoneValue);
    return phones;
  }

  private String serializePhone(FieldResolution phoneResolution) {
    Object value = phoneResolution.resolvedValue();
    if (value == null) {
      return null;
    }

    if (value instanceof List) {
      List<?> phones = (List<?>) value;
      if (phones.isEmpty()) {
        return null;
      }
      try {
        return objectMapper.writeValueAsString(phones);
      } catch (JsonProcessingException e) {
        log.warn("Failed to serialize phone list: {}", e.getMessage());
        return phones.toString();
      }
    }

    return value.toString();
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
            .timestamp(Instant.now(clock))
            .build();

    eventProducer.publish(masteredEvent);
  }

  private static String normalizeNationalId(String nationalId) {
    if (nationalId == null || nationalId.isBlank()) {
      return null;
    }
    return nationalId.trim().replaceAll("[^a-zA-Z0-9]", "");
  }
}
