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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer_golden")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerGoldenEntity {

  @Id private UUID id;

  @Column(name = "national_id", unique = true, nullable = false)
  private String nationalId;

  private String name;

  private String email;

  private String phone;

  @Column(name = "confidence_score", nullable = false)
  @Builder.Default
  private Short confidenceScore = 100;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "event_version")
  private Long eventVersion;

  @Column(name = "last_processed_event_timestamp")
  private Instant lastProcessedEventTimestamp;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_source_system")
  private String lastSourceSystem;
}
