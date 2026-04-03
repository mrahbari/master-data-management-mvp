/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.dto;

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
public class CustomerMasteredEvent {

  private UUID eventId;
  private UUID goldenRecordId;
  private String email;
  private String firstName;
  private String lastName;
  private String phone;
  private MasteringAction action;
  private Instant timestamp;

  public enum MasteringAction {
    CREATED,
    UPDATED,
    MERGED
  }
}
