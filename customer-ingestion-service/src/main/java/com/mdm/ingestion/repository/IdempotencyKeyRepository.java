/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mdm.ingestion.entity.IdempotencyKey;
import com.mdm.ingestion.entity.IdempotencyKey.IdempotencyStatus;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

  Optional<IdempotencyKey> findByKeyHash(String keyHash);

  Optional<IdempotencyKey> findByClientIdempotencyKey(String clientIdempotencyKey);

  @Modifying
  @Query(
      value =
          "INSERT INTO ingestion_idempotency_keys "
              + "(key_hash, client_idempotency_key, event_id, status, created_at, expires_at) "
              + "VALUES (:keyHash, :clientKey, :eventId, :status, :createdAt, :expiresAt) "
              + "ON CONFLICT (key_hash) DO NOTHING",
      nativeQuery = true)
  int insertIfNotExists(
      @Param("keyHash") String keyHash,
      @Param("clientKey") String clientKey,
      @Param("eventId") UUID eventId,
      @Param("status") String status,
      @Param("createdAt") Instant createdAt,
      @Param("expiresAt") Instant expiresAt);

  @Modifying
  @Query("UPDATE IdempotencyKey k SET k.status = :status WHERE k.keyHash = :keyHash")
  void completeByKeyHash(
      @Param("keyHash") String keyHash, @Param("status") IdempotencyStatus status);
}
