/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_raw")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRawEntity {

  @Id private UUID id;

  @Column(name = "event_id", unique = true, nullable = false)
  private UUID eventId;

  @Column(name = "national_id", nullable = false)
  private String nationalId;

  private String name;

  private String email;

  private String phone;

  @Column(name = "source_system", nullable = false)
  private String sourceSystem;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
  private String rawPayload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
