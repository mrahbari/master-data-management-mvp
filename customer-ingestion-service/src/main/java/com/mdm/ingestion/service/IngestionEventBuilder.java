/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mdm.ingestion.dto.CustomerRawEvent;

/**
 * Builds {@link CustomerRawEvent} instances from sanitized ingestion data.
 *
 * <p>This component encapsulates event construction logic, keeping the orchestration service
 * focused on workflow coordination. Follows the Single Responsibility Principle.
 */
@Component
public final class IngestionEventBuilder {

  /**
   * Creates a new CustomerRawEvent from sanitized input data.
   *
   * @param nationalId the normalized national ID (also serves as partition key)
   * @param name the customer name
   * @param email the customer email
   * @param phone the customer phone
   * @param sourceSystem the originating source system
   * @param timestamp the ingestion timestamp
   * @return a fully constructed event
   */
  public CustomerRawEvent build(
      String nationalId,
      String name,
      String email,
      String phone,
      String sourceSystem,
      Instant timestamp) {

    UUID eventId = UUID.randomUUID();
    UUID ingestionId = UUID.randomUUID();

    return CustomerRawEvent.builder()
        .eventId(eventId)
        .nationalId(nationalId)
        .name(name)
        .email(email)
        .phone(phone)
        .sourceSystem(sourceSystem)
        .timestamp(timestamp)
        .metadata(
            CustomerRawEvent.Metadata.builder()
                .ingestionId(ingestionId)
                .enrichmentStatus("FULL")
                .build())
        .build();
  }
}
