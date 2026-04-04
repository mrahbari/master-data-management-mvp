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
 * Customer Query Response DTO (Read Model).
 *
 * <p>This is the read side of CQRS - optimized for queries. Contains denormalized customer data
 * from the golden record.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerQueryResponse {

  private UUID id;
  private String nationalId;
  private String name;
  private String email;
  private String phone;
  private Short confidenceScore;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;
  private String lastSourceSystem;
}
