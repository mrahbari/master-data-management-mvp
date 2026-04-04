/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.exception;

/**
 * Classifies error types for retry and DLQ routing decisions.
 */
public enum ErrorType {
  /** Transient errors that should be retried (DB deadlock, timeout). */
  TRANSIENT,
  /** Permanent errors that should go directly to DLQ (constraint violation, invalid data). */
  PERMANENT,
  /** Business rule failures that should be logged and skipped. */
  BUSINESS
}
