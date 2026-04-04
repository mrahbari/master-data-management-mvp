/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents a raw customer event consumed from Kafka.
 *
 * <p>This DTO matches the event published by the Customer Ingestion Service. The {@code nationalId}
 * field is the canonical unique identifier for deduplication.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRawEvent {

  private UUID eventId;
  private String nationalId;
  private String name;
  private String email;
  private String phone;
  private String sourceSystem;
  private Instant timestamp;
  private Metadata metadata;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Metadata {
    private UUID ingestionId;
    private String enrichmentStatus;
  }
}
