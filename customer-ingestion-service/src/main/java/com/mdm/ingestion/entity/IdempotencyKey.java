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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents an idempotency key used to deduplicate ingestion requests.
 *
 * <p>This entity follows SOLID principles:
 *
 * <ul>
 *   <li><b>Single Responsibility:</b> Only manages idempotency state
 *   <li><b>Open/Closed:</b> Domain behavior methods extend functionality without modification
 *   <li><b>Liskov Substitution:</b> Proper equals/hashCode for JPA identity
 *   <li><b>Interface Segregation:</b> Minimal mutable state, factory method for creation
 *   <li><b>Dependency Inversion:</b> Clock injected via factory method for testability
 * </ul>
 */
@Entity
@Table(
    name = "ingestion_idempotency_keys",
    indexes = {
      @Index(name = "idx_idempotency_key_hash", columnList = "key_hash", unique = true),
      @Index(name = "idx_idempotency_client_key", columnList = "client_idempotency_key"),
      @Index(name = "idx_idempotency_expires_at", columnList = "expires_at")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

  @Id @GeneratedValue private UUID id;

  @Column(name = "key_hash", nullable = false, unique = true, length = 64)
  private String keyHash;

  @Column(name = "client_idempotency_key", length = 255)
  private String clientIdempotencyKey;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private IdempotencyStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /**
   * Factory method for creating a new idempotency key in PROCESSING state.
   *
   * @param keyHash the SHA-256 hash of the deterministic key (nationalId|sourceSystem)
   * @param clientIdempotencyKey the optional client-provided idempotency key
   * @param eventId the unique event ID for this ingestion
   * @param expiresAt when this key should expire
   * @param clock the clock to use for createdAt (injected for testability)
   * @return a new IdempotencyKey instance
   */
  public static IdempotencyKey createProcessing(
      String keyHash,
      String clientIdempotencyKey,
      UUID eventId,
      Instant expiresAt,
      java.time.Clock clock) {
    return new IdempotencyKey(keyHash, clientIdempotencyKey, eventId, expiresAt, clock);
  }

  IdempotencyKey(
      String keyHash,
      String clientIdempotencyKey,
      UUID eventId,
      Instant expiresAt,
      java.time.Clock clock) {
    if (keyHash == null || keyHash.isBlank()) {
      throw new IllegalArgumentException("keyHash is required");
    }
    if (eventId == null) {
      throw new IllegalArgumentException("eventId is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
    if (clock == null) {
      throw new IllegalArgumentException("clock is required");
    }
    this.keyHash = keyHash;
    this.clientIdempotencyKey = clientIdempotencyKey;
    this.eventId = eventId;
    this.status = IdempotencyStatus.PROCESSING;
    this.createdAt = Instant.now(clock);
    this.expiresAt = expiresAt;
  }

  /** Domain behavior: mark this key as successfully completed. */
  public void markCompleted() {
    this.status = IdempotencyStatus.COMPLETED;
  }

  /** Domain behavior: mark this key as failed to allow retry after backoff. */
  public void markFailed() {
    this.status = IdempotencyStatus.FAILED;
  }

  /** Check if this key has expired relative to the given time. */
  public boolean isExpiredAt(Instant instant) {
    return expiresAt.isBefore(instant);
  }

  /** Check if this key is still valid (not expired). */
  public boolean isValidAt(Instant instant) {
    return !expiresAt.isBefore(instant);
  }

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IdempotencyKey that = (IdempotencyKey) o;
    return Objects.equals(keyHash, that.keyHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(keyHash);
  }

  @Override
  public String toString() {
    return "IdempotencyKey{keyHash='%s', status=%s, eventId=%s}"
        .formatted(keyHash, status, eventId);
  }

  public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
  }
}
