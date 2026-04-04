/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

/**
 * Strategy interface for resolving field conflicts.
 *
 * <p>Each implementation represents a different conflict resolution strategy.
 */
public interface ConflictResolver {
  /**
   * Resolves a field conflict according to the given configuration.
   *
   * @param conflict the conflict details
   * @param config the resolution configuration
   * @return the resolution result with the resolved value and reason
   */
  FieldResolution resolve(FieldConflict conflict, ResolutionConfig config);
}
