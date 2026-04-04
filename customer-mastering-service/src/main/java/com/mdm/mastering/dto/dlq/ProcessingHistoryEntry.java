/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.dto.dlq;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Represents a single processing attempt for a DLQ event. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingHistoryEntry {

  private int attempt;
  private Instant timestamp;
  private String error;
}
