/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

import org.springframework.stereotype.Component;

/**
 * LATEST_UPDATE strategy: The value with the most recent timestamp wins.
 *
 * <p>This is the default general-purpose strategy for most fields.
 */
@Component
public class LatestUpdateResolver implements ConflictResolver {

  @Override
  public FieldResolution resolve(FieldConflict conflict, ResolutionConfig config) {
    if (!conflict.hasConflict()) {
      return FieldResolution.unchanged(conflict.currentValue(), "No conflict detected");
    }

    if (conflict.incomingTimestamp().isAfter(conflict.currentTimestamp())) {
      return FieldResolution.changed(
          conflict.incomingValue(),
          String.format(
              "%s event has later timestamp (%s > %s)",
              conflict.incomingSource(),
              conflict.incomingTimestamp(),
              conflict.currentTimestamp()));
    } else if (conflict.currentTimestamp().isAfter(conflict.incomingTimestamp())) {
      return FieldResolution.unchanged(
          conflict.currentValue(),
          String.format(
              "Current value has later timestamp (%s > %s)",
              conflict.currentTimestamp(), conflict.incomingTimestamp()));
    } else {
      // Same timestamp: prefer incoming value as tiebreaker
      return FieldResolution.changed(
          conflict.incomingValue(),
          String.format(
              "Same timestamp, preferring incoming value from %s", conflict.incomingSource()));
    }
  }
}
