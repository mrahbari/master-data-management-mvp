/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRawEvent {

  private UUID eventId;
  private String email;
  private String firstName;
  private String lastName;
  private String phone;
  private String sourceSystem;
  private Instant timestamp;
}
