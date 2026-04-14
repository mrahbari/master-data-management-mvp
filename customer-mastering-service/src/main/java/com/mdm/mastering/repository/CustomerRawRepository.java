/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mdm.mastering.entity.CustomerRawEntity;

@Repository
public interface CustomerRawRepository extends JpaRepository<CustomerRawEntity, UUID> {

  Optional<CustomerRawEntity> findByEventId(UUID eventId);

  boolean existsByEventId(UUID eventId);

  /**
   * Atomically inserts a raw event record, skipping if the eventId already exists. Returns the
   * number of rows inserted (1 = new, 0 = duplicate).
   */
  @Modifying
  @Query(
      value =
          """
      INSERT INTO customer_raw (id, event_id, national_id, name, email, phone, source_system, raw_payload, created_at)
      VALUES (:id, :eventId, :nationalId, :name, :email, :phone, :sourceSystem, :rawPayload, :createdAt)
      ON CONFLICT (event_id) DO NOTHING
      """,
      nativeQuery = true)
  int saveIfNotExists(
      @Param("id") UUID id,
      @Param("eventId") UUID eventId,
      @Param("nationalId") String nationalId,
      @Param("name") String name,
      @Param("email") String email,
      @Param("phone") String phone,
      @Param("sourceSystem") String sourceSystem,
      @Param("rawPayload") String rawPayload,
      @Param("createdAt") Instant createdAt);
}
