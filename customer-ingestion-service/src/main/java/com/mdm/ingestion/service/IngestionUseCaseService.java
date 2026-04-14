/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.ingestion.dto.CustomerIngestionRequest;
import com.mdm.ingestion.dto.CustomerRawEvent;
import com.mdm.ingestion.entity.OutboxEvent;
import com.mdm.ingestion.exception.ConcurrentProcessingException;
import com.mdm.ingestion.repository.OutboxEventRepository;
import com.mdm.ingestion.service.IdempotencyService.IdempotencyResult;
import com.mdm.ingestion.service.IngestionInputSanitizer.SanitizedRequest;
import com.mdm.ingestion.util.SensitiveDataMasker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the customer ingestion use case.
 *
 * <p>This service follows the Single Responsibility Principle by focusing solely on workflow
 * orchestration. Cross-cutting concerns are delegated to specialized components:
 *
 * <ul>
 *   <li>{@link IngestionInputSanitizer} - input normalization and validation
 *   <li>{@link IdempotencyService} - idempotency resolution
 *   <li>{@link IngestionEventBuilder} - event construction
 *   <li>{@link CustomerKafkaProducer} - Kafka publishing
 * </ul>
 *
 * <p><b>nationalId</b> is used as the canonical unique identifier throughout the system:
 *
 * <ul>
 *   <li>Primary deduplication key (via deterministic idempotency key)
 *   <li>Kafka partition key (ensures per-customer ordering)
 *   <li>Stored in the event payload for downstream processing
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class IngestionUseCaseService {

  private final OutboxEventRepository outboxRepository;
  private final IdempotencyService idempotencyService;
  private final IdempotencyFailureService idempotencyFailureService;
  private final IngestionInputSanitizer inputSanitizer;
  private final IngestionEventBuilder eventBuilder;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Value("${kafka.event-version:1.0}")
  private String eventVersion;

  /**
   * Ingests a customer ingestion request with idempotency guarantees.
   *
   * @param request the raw ingestion request
   * @param idempotencyKeyHeader the optional client-provided idempotency key header
   * @return response indicating whether the request was accepted or cached
   */
  @Transactional
  public IngestionResponse ingest(CustomerIngestionRequest request, String idempotencyKeyHeader) {
    // Step 1: Sanitize and validate input
    SanitizedRequest sanitized = inputSanitizer.sanitize(request);

    // Step 2: Resolve idempotency
    Instant timestamp = Instant.now(clock);
    IdempotencyResult result =
        idempotencyService.processKey(idempotencyKeyHeader, sanitized.nationalId(), sanitized.sourceSystem());

    // Step 3: Handle result
    return switch (result) {
      case IdempotencyService.IdempotencyHit hit -> handleIdempotencyHit(hit);
      case IdempotencyService.IdempotencyExpired expired ->
          processNewRequest(sanitized, expired.keyHash(), timestamp);
      case IdempotencyService.IdempotencyMiss miss ->
          processNewRequest(sanitized, miss.keyHash(), timestamp);
      case IdempotencyService.IdempotencyProcessing processing -> handleProcessing(processing);
    };
  }

  // ─── Private handlers ──────────────────────────────────────────────────────

  private IngestionResponse handleIdempotencyHit(IdempotencyService.IdempotencyHit hit) {
    log.info(
        "Duplicate request detected: keyHash={}, eventId={}",
        SensitiveDataMasker.maskHash(hit.keyHash()),
        hit.eventId());
    return new IngestionResponse(hit.eventId(), IngestionStatus.CACHED);
  }

  private IngestionResponse handleProcessing(IdempotencyService.IdempotencyProcessing processing) {
    log.info(
        "Request still processing: keyHash={}", SensitiveDataMasker.maskHash(processing.keyHash()));
    throw new ConcurrentProcessingException(processing.keyHash());
  }

  private IngestionResponse processNewRequest(
      SanitizedRequest sanitized, String keyHash, Instant timestamp) {
    // Build event
    CustomerRawEvent event =
        eventBuilder.build(
            sanitized.nationalId(),
            sanitized.name(),
            sanitized.email(),
            sanitized.phone(),
            sanitized.sourceSystem(),
            timestamp);

    // Write to transactional outbox (solves dual-write problem)
    // The OutboxPublisher will asynchronously publish to Kafka
    try {
      String payload = objectMapper.writeValueAsString(event);
      OutboxEvent outbox =
          OutboxEvent.create(
              "CUSTOMER",
              sanitized.nationalId(),
              "CUSTOMER_RAW_EVENT",
              payload,
              keyHash,
              eventVersion,
              timestamp);
      outboxRepository.save(outbox);

      // Mark idempotency key as completed immediately since the outbox write succeeded
      idempotencyService.completeKey(keyHash, event.getEventId());

      log.info(
          "Successfully wrote event to outbox: eventId={}, nationalId={}",
          event.getEventId(),
          SensitiveDataMasker.maskNationalId(sanitized.nationalId()));
    } catch (JsonProcessingException e) {
      idempotencyFailureService.failKey(keyHash, event.getEventId());
      throw new IllegalStateException("Failed to serialize event payload for outbox", e);
    }

    return new IngestionResponse(event.getEventId(), IngestionStatus.ACCEPTED);
  }

  // ─── Response types ────────────────────────────────────────────────────────

  public record IngestionResponse(UUID eventId, IngestionStatus status) {}

  public enum IngestionStatus {
    ACCEPTED,
    CACHED
  }
}
