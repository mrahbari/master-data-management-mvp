/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_raw")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRawEntity {

  @Id private UUID id;

  @Column(name = "event_id", unique = true, nullable = false)
  private UUID eventId;

  @Column(nullable = false)
  private String email;

  private String firstName;

  private String lastName;

  private String phone;

  @Column(name = "source_system", nullable = false)
  private String sourceSystem;

  @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
  private String rawPayload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
