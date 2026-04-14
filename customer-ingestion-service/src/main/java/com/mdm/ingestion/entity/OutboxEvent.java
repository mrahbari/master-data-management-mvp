/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Transactional outbox for reliable event publishing.
 *
 * <p>This entity ensures that event payloads and idempotency records are written in the same
 * database transaction, solving the dual-write problem. A separate publisher reads unprocessed
 * outbox entries and publishes them to Kafka.
 */
@Entity
@Table(
    name = "outbox_events",
    indexes = {
      @Index(name = "idx_outbox_processed", columnList = "processed"),
      @Index(name = "idx_outbox_created_at", columnList = "created_at"),
      @Index(name = "idx_outbox_aggregate_type", columnList = "aggregate_type"),
      @Index(name = "idx_outbox_retry", columnList = "retry_count,created_at")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

  @Id @GeneratedValue private UUID id;

  @Column(name = "aggregate_type", nullable = false, length = 50)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, length = 255)
  private String aggregateId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(name = "event_version", nullable = false, length = 20)
  private String eventVersion = "1.0";

  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "headers", columnDefinition = "jsonb")
  private String headers;

  @Column(name = "idempotency_key_hash", length = 64)
  private String idempotencyKeyHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "processed", nullable = false)
  private boolean processed = false;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "last_error", columnDefinition = "text")
  private String lastError;

  /** Factory method for creating a new outbox event. */
  public static OutboxEvent create(
      String aggregateType,
      String aggregateId,
      String eventType,
      String payload,
      String idempotencyKeyHash,
      String eventVersion,
      Instant createdAt) {
    OutboxEvent event = new OutboxEvent();
    event.aggregateType = aggregateType;
    event.aggregateId = aggregateId;
    event.eventType = eventType;
    event.payload = payload;
    event.idempotencyKeyHash = idempotencyKeyHash;
    event.eventVersion = eventVersion != null ? eventVersion : "1.0";
    event.createdAt = createdAt;
    event.processed = false;
    event.retryCount = 0;
    return event;
  }

  /** Marks this event as successfully published to Kafka. */
  public void markProcessed() {
    this.processed = true;
    this.processedAt = Instant.now();
  }

  /** Marks this event for retry after a publish failure. */
  public void markForRetry(String error) {
    this.retryCount++;
    this.lastError = error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
  }

  /** Returns whether this event has exceeded the max retry threshold. */
  public boolean isExhausted(int maxRetries) {
    return this.retryCount >= maxRetries;
  }

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OutboxEvent that = (OutboxEvent) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
