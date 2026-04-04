/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

/** Represents the result of resolving a field conflict. */
public record FieldResolution(Object resolvedValue, String resolutionReason, boolean valueChanged) {

  /** Creates a resolution where the value was not changed. */
  public static FieldResolution unchanged(Object value, String reason) {
    return new FieldResolution(value, reason, false);
  }

  /** Creates a resolution where the value was changed. */
  public static FieldResolution changed(Object value, String reason) {
    return new FieldResolution(value, reason, true);
  }
}
