/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mdm.ingestion.service.OutboxPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Management controller for monitoring and operating the transactional outbox.
 *
 * <p>Provides HTTP endpoints to:
 *
 * <ul>
 *   <li>View outbox statistics and pending event count
 *   <li>Manually retry a specific outbox event
 *   <li>Force requeue a processed event for re-processing
 * </ul>
 *
 * <p>All endpoints require ADMIN role for security.
 */
@Slf4j
@RestController
@RequestMapping("/api/outbox")
@RequiredArgsConstructor
public class OutboxManagementController {

  private final OutboxPublisher outboxPublisher;

  /**
   * Get outbox statistics including pending event count.
   *
   * @return outbox statistics
   */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/stats")
  public ResponseEntity<Map<String, Object>> getOutboxStats() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("pendingEvents", outboxPublisher.getPendingCount());
    stats.put("description", "Pending events are outbox records waiting to be published to Kafka");

    log.info("Outbox stats requested: pending={}", stats.get("pendingEvents"));
    return ResponseEntity.ok(stats);
  }

  /**
   * Manually retry a specific outbox event by ID.
   *
   * @param eventId the outbox event ID to retry
   * @return retry result
   */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/retry/{eventId}")
  public ResponseEntity<Map<String, Object>> retryOutboxEvent(@PathVariable UUID eventId) {
    Map<String, Object> result = new HashMap<>();

    boolean success = outboxPublisher.retryEvent(eventId);
    if (success) {
      result.put("success", true);
      result.put("message", "Outbox event retried and published successfully");
      result.put("eventId", eventId.toString());
      log.info("Outbox event retried successfully: id={}", eventId);
      return ResponseEntity.ok(result);
    } else {
      result.put("success", false);
      result.put("message", "Outbox event not found or publish failed");
      result.put("eventId", eventId.toString());
      log.warn("Outbox event retry failed: id={}", eventId);
      return ResponseEntity.badRequest().body(result);
    }
  }

  /**
   * Force requeue a processed outbox event for re-processing. This is an admin operation to recover
   * from edge cases.
   *
   * @param eventId the outbox event ID to requeue
   * @return requeue result
   */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/force-requeue/{eventId}")
  public ResponseEntity<Map<String, Object>> forceRequeueEvent(@PathVariable UUID eventId) {
    Map<String, Object> result = new HashMap<>();

    boolean success = outboxPublisher.forceRequeueEvent(eventId);
    if (success) {
      result.put("success", true);
      result.put("message", "Outbox event force requeued successfully");
      result.put("eventId", eventId.toString());
      log.info("Outbox event force requeued: id={}", eventId);
      return ResponseEntity.ok(result);
    } else {
      result.put("success", false);
      result.put("message", "Outbox event not found or already unprocessed");
      result.put("eventId", eventId.toString());
      log.warn("Outbox event force requeue failed: id={}", eventId);
      return ResponseEntity.badRequest().body(result);
    }
  }
}
