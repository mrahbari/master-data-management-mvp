/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.dto.dlq.DlqEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DLQ Management Controller for inspecting and reprocessing Dead Letter Queue messages [Only Demo
 * purpose!].
 *
 * <p>Provides HTTP endpoints to:
 *
 * <ul>
 *   <li>View DLQ statistics and configuration
 *   <li>Manually reprocess DLQ messages back to the raw topic
 *   <li>Get DLQ message structure reference
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DlqManagementController {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @Value("${kafka.topics.customer-raw:customer.raw}")
  private String rawTopic;

  @Value("${kafka.topics.customer-dlq:customer.dlq}")
  private String dlqTopic;

  @Value("${mdm.mastering.retry.maxAttempts:3}")
  private int maxRetries;

  @Value("${mdm.mastering.retry.backoff.initialInterval:1000}")
  private long retryInitialInterval;

  @Value("${mdm.mastering.retry.backoff.maxInterval:10000}")
  private long retryMaxInterval;

  @Value("${mdm.mastering.retry.backoff.multiplier:2.0}")
  private double retryMultiplier;

  /**
   * Get DLQ statistics and configuration.
   *
   * @return DLQ topic configuration and statistics
   */
  @GetMapping("/stats")
  public ResponseEntity<Map<String, Object>> getDlqStats() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("dlqTopic", dlqTopic);
    stats.put("rawTopic", rawTopic);
    stats.put("maxRetries", maxRetries);
    stats.put("retryInitialInterval", retryInitialInterval + "ms");
    stats.put("retryMaxInterval", retryMaxInterval + "ms");
    stats.put("retryMultiplier", retryMultiplier);
    stats.put(
        "errorTypes",
        Map.of(
            "TRANSIENT", "Retryable errors (DB deadlock, timeout) - retried 3 times",
            "PERMANENT", "Non-retryable errors (constraint violation) - directly to DLQ",
            "BUSINESS", "Business rule failures - logged and skipped"));

    log.info("DLQ stats requested");
    return ResponseEntity.ok(stats);
  }

  /**
   * Manually reprocess a DLQ message by publishing it back to the raw topic.
   *
   * <p>This endpoint extracts the original event from the DLQ wrapper and republishes it to the
   * customer.raw topic for reprocessing.
   *
   * @param dlqEvent the DLQ event containing the original event and error details
   * @return reprocessing result
   */
  @PostMapping("/reprocess")
  public ResponseEntity<Map<String, Object>> reprocessDlqMessage(@RequestBody DlqEvent dlqEvent) {
    Map<String, Object> result = new HashMap<>();

    try {
      // Extract original event from DLQ wrapper
      Object originalEventObj = dlqEvent.getOriginalEvent();
      if (originalEventObj == null) {
        result.put("success", false);
        result.put("error", "No original event found in DLQ message");
        return ResponseEntity.badRequest().body(result);
      }

      // Convert to CustomerRawEvent if needed
      CustomerRawEvent rawEvent;
      if (originalEventObj instanceof CustomerRawEvent) {
        rawEvent = (CustomerRawEvent) originalEventObj;
      } else {
        // Convert from map/object to CustomerRawEvent
        // Handle eventId as String or UUID
        Map<String, Object> eventMap = objectMapper.convertValue(originalEventObj, Map.class);
        Object eventIdObj = eventMap.get("eventId");
        UUID eventId;
        if (eventIdObj instanceof UUID) {
          eventId = (UUID) eventIdObj;
        } else if (eventIdObj instanceof String) {
          try {
            eventId = UUID.fromString((String) eventIdObj);
          } catch (IllegalArgumentException e) {
            // Generate new UUID if string is not valid UUID format
            eventId = UUID.randomUUID();
          }
        } else {
          eventId = UUID.randomUUID();
        }

        rawEvent =
            CustomerRawEvent.builder()
                .eventId(eventId)
                .nationalId((String) eventMap.get("nationalId"))
                .name((String) eventMap.get("name"))
                .email((String) eventMap.get("email"))
                .phone((String) eventMap.get("phone"))
                .sourceSystem((String) eventMap.get("sourceSystem"))
                .build();
      }

      // Validate required fields
      if (rawEvent.getNationalId() == null || rawEvent.getNationalId().isBlank()) {
        result.put("success", false);
        result.put("error", "nationalId is required in the original event");
        return ResponseEntity.badRequest().body(result);
      }

      if (rawEvent.getSourceSystem() == null || rawEvent.getSourceSystem().isBlank()) {
        result.put("success", false);
        result.put("error", "sourceSystem is required in the original event");
        return ResponseEntity.badRequest().body(result);
      }

      // Generate new event ID for reprocessing
      String newEventId = UUID.randomUUID().toString();

      // Create a new event with the new ID (since CustomerRawEvent is immutable with @Getter only)
      CustomerRawEvent eventToReprocess =
          CustomerRawEvent.builder()
              .eventId(UUID.fromString(newEventId))
              .nationalId(rawEvent.getNationalId())
              .name(rawEvent.getName())
              .email(rawEvent.getEmail())
              .phone(rawEvent.getPhone())
              .sourceSystem(rawEvent.getSourceSystem())
              .timestamp(rawEvent.getTimestamp())
              .build();

      // Serialize and publish to raw topic
      String eventJson = objectMapper.writeValueAsString(eventToReprocess);

      kafkaTemplate
          .send(rawTopic, eventToReprocess.getNationalId(), eventJson)
          .whenComplete(
              (recordMetadata, ex) -> {
                if (ex == null) {
                  log.info(
                      "Reprocessed DLQ message: eventId={}, nationalId={}, source={}, newEventId={}",
                      dlqEvent.getErrorDetails() != null
                          ? dlqEvent.getErrorDetails().getException()
                          : "unknown",
                      eventToReprocess.getNationalId(),
                      eventToReprocess.getSourceSystem(),
                      newEventId);
                } else {
                  log.error(
                      "Failed to reprocess DLQ message: eventId={}, error={}",
                      newEventId,
                      ex.getMessage(),
                      ex);
                }
              });

      result.put("success", true);
      result.put("message", "DLQ message reprocessed successfully");
      result.put("newEventId", newEventId);
      result.put("nationalId", eventToReprocess.getNationalId());
      result.put("sourceSystem", eventToReprocess.getSourceSystem());
      result.put("republishedTo", rawTopic);

      log.info(
          "DLQ message reprocessed: newEventId={}, nationalId={}",
          newEventId,
          eventToReprocess.getNationalId());

      return ResponseEntity.ok(result);

    } catch (JsonProcessingException e) {
      result.put("success", false);
      result.put("error", "Failed to serialize event: " + e.getMessage());
      log.error("Failed to serialize DLQ event for reprocessing", e);
      return ResponseEntity.badRequest().body(result);

    } catch (Exception e) {
      result.put("success", false);
      result.put("error", "Failed to reprocess DLQ message: " + e.getMessage());
      log.error("Failed to reprocess DLQ message", e);
      return ResponseEntity.internalServerError().body(result);
    }
  }

  /**
   * Get DLQ message structure reference documentation.
   *
   * @return DLQ message structure example and field descriptions
   */
  @GetMapping("/structure")
  public ResponseEntity<Map<String, Object>> getDlqStructure() {
    Map<String, Object> structure = new HashMap<>();

    // Example DLQ message
    Map<String, Object> example = new HashMap<>();
    example.put(
        "originalEvent",
        Map.of(
            "eventId", "abc123",
            "nationalId", "123456789012",
            "name", "John Doe",
            "email", "john@example.com",
            "phone", "+1-555-123-4567",
            "sourceSystem", "WEB",
            "timestamp", "2026-04-03T20:30:00Z"));
    example.put(
        "errorDetails",
        Map.of(
            "exception", "DataIntegrityViolationException",
            "message", "could not execute statement [SQL state 23505]...",
            "stackTrace", "org.springframework.dao.DataIntegrityViolationException..."));
    example.put(
        "processingHistory",
        List.of(
            Map.of(
                "attempt", 1,
                "timestamp", "2026-04-03T20:30:01Z",
                "error", "DeadlockLoserDataAccessException: Deadlock detected"),
            Map.of(
                "attempt", 2,
                "timestamp", "2026-04-03T20:30:03Z",
                "error", "DeadlockLoserDataAccessException: Deadlock detected"),
            Map.of(
                "attempt", 3,
                "timestamp", "2026-04-03T20:30:05Z",
                "error", "DeadlockLoserDataAccessException: Deadlock detected")));
    example.put("schemaVersion", "v1");

    structure.put("example", example);
    structure.put(
        "fields",
        Map.of(
            "originalEvent", "The original CustomerRawEvent that failed processing",
            "errorDetails.exception", "Full exception class name",
            "errorDetails.message", "Error message (truncated to 500 chars)",
            "errorDetails.stackTrace", "Full stack trace for debugging",
            "processingHistory", "Array of retry attempts with timestamps and errors",
            "schemaVersion", "DLQ message schema version (currently v1)"));
    structure.put(
        "errorTypes",
        Map.of(
            "TRANSIENT",
                Map.of(
                    "description", "Temporary failures that may resolve on retry",
                    "examples",
                        List.of(
                            "DeadlockLoserDataAccessException",
                            "QueryTimeoutException",
                            "LockAcquisitionException"),
                    "behavior", "Retried 3 times with exponential backoff, then sent to DLQ"),
            "PERMANENT",
                Map.of(
                    "description", "Non-retryable errors that won't resolve",
                    "examples",
                        List.of(
                            "DataIntegrityViolationException",
                            "ConstraintViolationException",
                            "MethodArgumentNotValidException"),
                    "behavior", "Immediately sent to DLQ without retry"),
            "BUSINESS",
                Map.of(
                    "description", "Business rule violations",
                    "examples", List.of("BusinessRuleValidationException"),
                    "behavior", "Logged and skipped, not retried")));

    return ResponseEntity.ok(structure);
  }

  /**
   * Get instructions for manual DLQ reprocessing via Kafka CLI.
   *
   * @return manual reprocessing instructions
   */
  @GetMapping("/manual-reprocess")
  public ResponseEntity<Map<String, Object>> getManualReprocessInstructions() {
    Map<String, Object> instructions = new HashMap<>();

    instructions.put("description", "Manual steps to reprocess DLQ messages using Kafka CLI tools");
    instructions.put(
        "steps",
        List.of(
            "1. Consume DLQ messages to identify the failed event:",
            "   docker compose exec kafka kafka-console-consumer \\",
            "     --bootstrap-server localhost:9092 \\",
            "     --topic " + dlqTopic + " \\",
            "     --from-beginning \\",
            "     --max-messages 10 \\",
            "     --timeout-ms 10000",
            "",
            "2. Extract the originalEvent JSON from the DLQ message",
            "",
            "3. Publish the original event back to the raw topic:",
            "   docker compose exec kafka kafka-console-producer \\",
            "     --bootstrap-server localhost:9092 \\",
            "     --topic " + rawTopic,
            "",
            "4. Paste the originalEvent JSON (one per line) and press Ctrl+D",
            "",
            "5. Monitor the mastering service logs to verify reprocessing:",
            "   docker compose logs -f customer-mastering-service | grep -i 'reprocess\\|success'"));

    instructions.put(
        "alternativeApproach",
        Map.of(
            "description", "Use the /api/dlq/reprocess endpoint",
            "method", "POST",
            "url", "/api/dlq/reprocess",
            "body",
                "Send the complete DLQ event JSON (including originalEvent, errorDetails, etc.)",
            "result",
                "The service will extract the original event and republish it to "
                    + rawTopic
                    + " topic"));

    return ResponseEntity.ok(instructions);
  }
}
