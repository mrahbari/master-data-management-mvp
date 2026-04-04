/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.mdm.mastering.conflict.ResolutionConfig.ConflictResolutionStrategy;
import com.mdm.mastering.conflict.ResolutionConfig.MergeStrategy;
import com.mdm.mastering.conflict.ResolutionConfig.MergeStrategy.MergeType;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for conflict resolution.
 *
 * <p>Loads from application.yml under {@code mdm.mastering.conflict-resolution}.
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "mdm.mastering.conflict-resolution")
public class ConflictResolutionConfig {

  private ConflictResolutionStrategy defaultStrategy = ConflictResolutionStrategy.LATEST_UPDATE;
  private Map<String, FieldConflictConfig> fieldStrategies = new HashMap<>();

  /**
   * Gets the resolution config for a specific field.
   *
   * @param fieldName the field name
   * @return the configuration for the field, or default config if not specified
   */
  public ResolutionConfig getConfigForField(String fieldName) {
    FieldConflictConfig fieldConfig = fieldStrategies.get(fieldName);
    if (fieldConfig == null) {
      return new ResolutionConfig(defaultStrategy);
    }

    MergeStrategy mergeStrategy = null;
    if (fieldConfig.getMergeStrategy() != null) {
      MergeConfig mergeConfig = fieldConfig.getMergeStrategy();
      mergeStrategy =
          new MergeStrategy(
              mergeConfig.getType() != null ? mergeConfig.getType() : MergeType.UNION,
              mergeConfig.getMaxValues() != null ? mergeConfig.getMaxValues() : 10);
    }

    return new ResolutionConfig(
        fieldConfig.getStrategy() != null ? fieldConfig.getStrategy() : defaultStrategy,
        fieldConfig.getTrustedSources(),
        mergeStrategy);
  }

  /** Field-level conflict resolution configuration. */
  @Setter
  @Getter
  public static class FieldConflictConfig {
    private ConflictResolutionStrategy strategy;
    private List<String> trustedSources;
    private MergeConfig mergeStrategy;
  }

  /** Merge-specific configuration. */
  @Setter
  @Getter
  public static class MergeConfig {
    private MergeType type;
    private Integer maxValues;
  }
}
