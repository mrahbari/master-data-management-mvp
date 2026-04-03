/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.service;

import com.mdm.ingestion.dto.CustomerIngestionRequest;
import com.mdm.ingestion.dto.CustomerRawEvent;
import com.mdm.ingestion.exception.ConcurrentProcessingException;
import com.mdm.ingestion.exception.KafkaPublishException;
import com.mdm.ingestion.service.IdempotencyService.IdempotencyResult;
import com.mdm.ingestion.service.IngestionInputSanitizer.SanitizedRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the customer ingestion use case.
 *
 * <p>This service follows the Single Responsibility Principle by focusing solely on
 * workflow orchestration. Cross-cutting concerns are delegated to specialized components:
 * <ul>
 *   <li>{@link IngestionInputSanitizer} - input normalization and validation</li>
 *   <li>{@link IdempotencyService} - idempotency resolution</li>
 *   <li>{@link IngestionEventBuilder} - event construction</li>
 *   <li>{@link CustomerKafkaProducer} - Kafka publishing</li>
 * </ul>
 *
 * <p><b>nationalId</b> is used as the canonical unique identifier throughout the system:
 * <ul>
 *   <li>Primary deduplication key (via deterministic idempotency key)</li>
 *   <li>Kafka partition key (ensures per-customer ordering)</li>
 *   <li>Stored in the event payload for downstream processing</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class IngestionUseCaseService {

  private final CustomerKafkaProducer kafkaProducer;
  private final IdempotencyService idempotencyService;
  private final IngestionInputSanitizer inputSanitizer;
  private final IngestionEventBuilder eventBuilder;
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
  public IngestionResponse ingest(
      CustomerIngestionRequest request, String idempotencyKeyHeader) {
    // Step 1: Sanitize and validate input
    SanitizedRequest sanitized = inputSanitizer.sanitize(request);

    // Step 2: Resolve idempotency
    Instant timestamp = Instant.now(clock);
    IdempotencyResult result =
        idempotencyService.processKey(
            idempotencyKeyHeader, sanitized.nationalId(), sanitized.sourceSystem());

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
    log.info("Duplicate request detected: keyHash={}, eventId={}", maskKey(hit.keyHash()), hit.eventId());
    return new IngestionResponse(hit.eventId(), IngestionStatus.CACHED);
  }

  private IngestionResponse handleProcessing(IdempotencyService.IdempotencyProcessing processing) {
    log.info("Request still processing: keyHash={}", maskKey(processing.keyHash()));
    throw new ConcurrentProcessingException(processing.keyHash());
  }

  private IngestionResponse processNewRequest(
      SanitizedRequest sanitized, String keyHash, Instant timestamp) {
    // Build event
    CustomerRawEvent event = eventBuilder.build(
        sanitized.nationalId(),
        sanitized.name(),
        sanitized.email(),
        sanitized.phone(),
        sanitized.sourceSystem(),
        timestamp);

    // Publish to Kafka
    try {
      // nationalId serves as the partition key for ordering guarantees
      kafkaProducer.send(event, sanitized.nationalId(), eventVersion).get();
      idempotencyService.completeKey(keyHash, event.getEventId());
      log.info(
          "Successfully published event to Kafka: eventId={}, nationalId={}",
          event.getEventId(),
          maskNationalId(sanitized.nationalId()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      idempotencyService.failKey(keyHash, event.getEventId());
      throw new KafkaPublishException(event.getEventId().toString(), keyHash, e);
    } catch (ExecutionException e) {
      idempotencyService.failKey(keyHash, event.getEventId());
      throw new KafkaPublishException(event.getEventId().toString(), keyHash, e);
    }

    return new IngestionResponse(event.getEventId(), IngestionStatus.ACCEPTED);
  }

  // ─── Masking helpers ───────────────────────────────────────────────────────

  private static String maskKey(String key) {
    if (key == null || key.length() <= 8) {
      return "***";
    }
    return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
  }

  private static String maskNationalId(String nationalId) {
    if (nationalId == null || nationalId.length() <= 4) {
      return "***";
    }
    return "***" + nationalId.substring(nationalId.length() - 4);
  }

  // ─── Response types ────────────────────────────────────────────────────────

  public record IngestionResponse(UUID eventId, IngestionStatus status) {}

  public enum IngestionStatus {
    ACCEPTED,
    CACHED
  }
}
