/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

import java.util.List;

/** Configuration for conflict resolution. */
public record ResolutionConfig(
    ConflictResolutionStrategy strategy, List<String> trustedSources, MergeStrategy mergeStrategy) {
  public ResolutionConfig(ConflictResolutionStrategy strategy) {
    this(strategy, null, null);
  }

  /** Supported conflict resolution strategies. */
  public enum ConflictResolutionStrategy {
    LATEST_UPDATE,
    TRUSTED_SOURCE,
    MOST_FREQUENT,
    NON_NULL,
    MERGE
  }

  /** Configuration for merge operations. */
  public record MergeStrategy(MergeType type, int maxValues) {

    public enum MergeType {
      UNION,
      APPEND
    }
  }
}
