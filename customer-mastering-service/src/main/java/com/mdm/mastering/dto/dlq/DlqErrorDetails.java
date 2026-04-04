/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.dto.dlq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Captures error details for a failed DLQ event.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqErrorDetails {

  private String exception;
  private String message;
  private String stackTrace;
}
