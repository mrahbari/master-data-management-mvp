/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * TRUSTED_SOURCE strategy: Prefer values from specific trusted source systems.
 *
 * <p>This strategy is used for regulatory or high-confidence data sources.
 */
@Component
public class TrustedSourceResolver implements ConflictResolver {

  @Override
  public FieldResolution resolve(FieldConflict conflict, ResolutionConfig config) {
    if (!conflict.hasConflict()) {
      return FieldResolution.unchanged(conflict.currentValue(), "No conflict detected");
    }

    List<String> trustedSources = config.trustedSources();
    if (trustedSources == null || trustedSources.isEmpty()) {
      // Fallback to latest update if no trusted sources configured
      return resolveWithFallback(conflict);
    }

    boolean currentIsTrusted = trustedSources.contains(conflict.currentSource());
    boolean incomingIsTrusted = trustedSources.contains(conflict.incomingSource());

    if (currentIsTrusted && !incomingIsTrusted) {
      return FieldResolution.unchanged(
          conflict.currentValue(),
          String.format("%s is a trusted source, keeping current value", conflict.currentSource()));
    } else if (!currentIsTrusted && incomingIsTrusted) {
      return FieldResolution.changed(
          conflict.incomingValue(),
          String.format(
              "%s is a trusted source, preferring incoming value", conflict.incomingSource()));
    } else if (currentIsTrusted && incomingIsTrusted) {
      // Both are trusted: prefer latest timestamp as tiebreaker
      if (conflict.incomingTimestamp().isAfter(conflict.currentTimestamp())) {
        return FieldResolution.changed(
            conflict.incomingValue(),
            String.format(
                "Both sources trusted, %s has later timestamp", conflict.incomingSource()));
      } else {
        return FieldResolution.unchanged(
            conflict.currentValue(),
            String.format(
                "Both sources trusted, %s has later or equal timestamp", conflict.currentSource()));
      }
    } else {
      // Neither is trusted: fallback to latest update
      return resolveWithFallback(conflict);
    }
  }

  private FieldResolution resolveWithFallback(FieldConflict conflict) {
    if (conflict.incomingTimestamp().isAfter(conflict.currentTimestamp())) {
      return FieldResolution.changed(
          conflict.incomingValue(), "No trusted sources configured, falling back to latest update");
    } else {
      return FieldResolution.unchanged(
          conflict.currentValue(), "No trusted sources configured, falling back to latest update");
    }
  }
}
