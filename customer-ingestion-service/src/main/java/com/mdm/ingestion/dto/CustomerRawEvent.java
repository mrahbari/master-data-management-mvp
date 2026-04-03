/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents a raw customer event published to Kafka.
 *
 * <p>This DTO is designed to be immutable after construction:
 * <ul>
 *   <li>Uses {@code @Getter} instead of {@code @Data} to avoid generating setters</li>
 *   <li>Builder pattern enforces complete construction</li>
 *   <li>All fields are final via the builder</li>
 * </ul>
 *
 * <p>The {@code nationalId} field serves as the canonical unique identifier:
 * <ul>
 *   <li>Used as the Kafka partition key for ordering guarantees</li>
 *   <li>Used for idempotency deduplication</li>
 *   <li>Stored in the event payload for downstream processing</li>
 * </ul>
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

  /**
   * Metadata associated with the ingestion event.
   */
  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Metadata {
    private UUID ingestionId;
    private String enrichmentStatus;
  }
}
