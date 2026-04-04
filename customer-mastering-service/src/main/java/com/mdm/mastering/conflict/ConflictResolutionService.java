/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.mdm.mastering.conflict.ResolutionConfig.ConflictResolutionStrategy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Service for orchestrating field conflict resolution.
 *
 * <p>Delegates to strategy-specific resolvers and tracks metrics.
 */
@Service
public class ConflictResolutionService {

  private final Map<ConflictResolutionStrategy, ConflictResolver> resolvers;
  private final ConflictResolutionConfig config;
  private final ConflictMetrics metrics;

  public ConflictResolutionService(
      LatestUpdateResolver latestUpdateResolver,
      TrustedSourceResolver trustedSourceResolver,
      MostFrequentResolver mostFrequentResolver,
      NonNullResolver nonNullResolver,
      MergeResolver mergeResolver,
      ConflictResolutionConfig config,
      MeterRegistry meterRegistry) {
    this.config = config;
    this.metrics = new ConflictMetrics(meterRegistry);

    this.resolvers = new EnumMap<>(ConflictResolutionStrategy.class);
    this.resolvers.put(ConflictResolutionStrategy.LATEST_UPDATE, latestUpdateResolver);
    this.resolvers.put(ConflictResolutionStrategy.TRUSTED_SOURCE, trustedSourceResolver);
    this.resolvers.put(ConflictResolutionStrategy.MOST_FREQUENT, mostFrequentResolver);
    this.resolvers.put(ConflictResolutionStrategy.NON_NULL, nonNullResolver);
    this.resolvers.put(ConflictResolutionStrategy.MERGE, mergeResolver);
  }

  /**
   * Resolves a field conflict using the configured strategy for the field.
   *
   * @param conflict the conflict details
   * @return the resolution result
   */
  public FieldResolution resolve(FieldConflict conflict) {
    ResolutionConfig fieldConfig = config.getConfigForField(conflict.fieldName());
    ConflictResolutionStrategy strategy = fieldConfig.strategy();

    ConflictResolver resolver = resolvers.get(strategy);
    if (resolver == null) {
      throw new IllegalStateException("No resolver found for strategy: " + strategy);
    }

    FieldResolution resolution = resolver.resolve(conflict, fieldConfig);
    metrics.recordConflictResolved(strategy);

    return resolution;
  }

  /** Gets the configuration for a field. */
  public ResolutionConfig getConfigForField(String fieldName) {
    return config.getConfigForField(fieldName);
  }

  /** Metrics tracker for conflict resolution. */
  private static class ConflictMetrics {
    private final Map<ConflictResolutionStrategy, Counter> counters;

    ConflictMetrics(MeterRegistry meterRegistry) {
      this.counters = new EnumMap<>(ConflictResolutionStrategy.class);
      for (ConflictResolutionStrategy strategy : ConflictResolutionStrategy.values()) {
        this.counters.put(
            strategy,
            Counter.builder("conflicts_resolved_total")
                .description("Total number of conflicts resolved")
                .tag("strategy", strategy.name())
                .register(meterRegistry));
      }
    }

    void recordConflictResolved(ConflictResolutionStrategy strategy) {
      Counter counter = counters.get(strategy);
      if (counter != null) {
        counter.increment();
      }
    }
  }
}
