/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

import org.springframework.stereotype.Component;

/**
 * NON_NULL strategy: Keep the first non-null value.
 *
 * <p>If the current value is non-null, keep it. Otherwise, use the incoming value. This strategy is
 * useful for optional fields where any value is better than null.
 */
@Component
public class NonNullResolver implements ConflictResolver {

  @Override
  public FieldResolution resolve(FieldConflict conflict, ResolutionConfig config) {
    // Note: hasConflict() already checks for nulls, but we handle it explicitly here
    // for the NON_NULL strategy semantics.

    if (conflict.currentValue() != null) {
      if (conflict.incomingValue() == null) {
        return FieldResolution.unchanged(
            conflict.currentValue(),
            "Current value is non-null, incoming is null - keeping current");
      }
      // Both non-null: this is a real conflict
      if (conflict.currentValue().equals(conflict.incomingValue())) {
        return FieldResolution.unchanged(conflict.currentValue(), "Values are identical");
      }
      // Both non-null and different: keep current as it was first non-null
      return FieldResolution.unchanged(
          conflict.currentValue(),
          String.format(
              "Both values non-null, keeping first seen value from %s", conflict.currentSource()));
    } else {
      // Current is null, use incoming
      return FieldResolution.changed(
          conflict.incomingValue(), "Current value is null, using incoming value");
    }
  }
}
