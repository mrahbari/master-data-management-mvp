/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mdm.ingestion.entity.OutboxEvent;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  /** Find unprocessed outbox events for publishing, ordered by creation time. */
  @Query("SELECT o FROM OutboxEvent o WHERE o.processed = false ORDER BY o.createdAt ASC")
  List<OutboxEvent> findUnprocessed(Pageable pageable);

  /** Find a single unprocessed event by ID for manual retry. */
  @Query("SELECT o FROM OutboxEvent o WHERE o.id = :id AND o.processed = false")
  Optional<OutboxEvent> findUnprocessedById(@Param("id") UUID id);

  /** Count pending (unprocessed) outbox events. */
  @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.processed = false")
  long countUnprocessed();

  /** Mark an outbox event as processed. */
  @Modifying
  @Query(
      "UPDATE OutboxEvent o SET o.processed = true, o.processedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
  int markProcessed(@Param("id") UUID id);

  /** Increment retry count and record the last error. */
  @Modifying
  @Query(
      "UPDATE OutboxEvent o SET o.retryCount = o.retryCount + 1, o.lastError = :error WHERE o.id = :id")
  int incrementRetry(@Param("id") UUID id, @Param("error") String error);

  /** Manually force an event to be re-queued for processing. */
  @Modifying
  @Query(
      "UPDATE OutboxEvent o SET o.processed = false, o.retryCount = 0, o.lastError = NULL WHERE o.id = :id AND o.processed = true")
  int forceRequeue(@Param("id") UUID id);
}
