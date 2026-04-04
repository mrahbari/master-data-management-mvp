/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

import org.springframework.stereotype.Component;

/**
 * MOST_FREQUENT strategy: Keep the value seen most often.
 *
 * <p>For simple binary conflicts (current vs incoming), this prefers the current value as it
 * represents the historically observed value. In a full implementation, this would analyze all
 * historical values to determine the mode.
 *
 * <p>This strategy is best for stable attributes that rarely change.
 */
@Component
public class MostFrequentResolver implements ConflictResolver {

  @Override
  public FieldResolution resolve(FieldConflict conflict, ResolutionConfig config) {
    if (!conflict.hasConflict()) {
      return FieldResolution.unchanged(conflict.currentValue(), "No conflict detected");
    }

    // In a binary conflict, the current value represents the historically observed value.
    // In a production system, this would query historical frequency data.
    // For MVP, we prefer the current value as it has been observed longer.
    return FieldResolution.unchanged(
        conflict.currentValue(),
        String.format(
            "Current value '%s' preferred as most frequent (observed longer than incoming '%s' from %s)",
            conflict.currentValue(), conflict.incomingValue(), conflict.incomingSource()));
  }
}
