/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.dto.dlq;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Dead Letter Queue event wrapper.
 *
 * <p>Contains the original event, error details, and full processing history for failed messages
 * that exhausted all retry attempts.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqEvent {

  private Object originalEvent;
  private DlqErrorDetails errorDetails;
  @Builder.Default private List<ProcessingHistoryEntry> processingHistory = new ArrayList<>();
  private String schemaVersion;
}
