/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

import java.time.Instant;

/**
 * Represents a conflict between two values for the same field.
 *
 * <p>A conflict occurs when both current and incoming values are non-null and different.
 */
public record FieldConflict(
    String fieldName,
    Object currentValue,
    Object incomingValue,
    Instant currentTimestamp,
    Instant incomingTimestamp,
    String currentSource,
    String incomingSource) {

  /** Returns true if there is an actual conflict (both values non-null and different). */
  public boolean hasConflict() {
    if (currentValue == null || incomingValue == null) {
      return false;
    }
    return !currentValue.equals(incomingValue);
  }
}
