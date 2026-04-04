/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.mdm.mastering.conflict.ResolutionConfig.ConflictResolutionStrategy;
import com.mdm.mastering.conflict.ResolutionConfig.MergeStrategy;
import com.mdm.mastering.conflict.ResolutionConfig.MergeStrategy.MergeType;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive tests for conflict resolution strategies.
 *
 * <p>Test scenarios:
 *
 * <ol>
 *   <li>LATEST_UPDATE strategy - most recent timestamp wins
 *   <li>TRUSTED_SOURCE strategy - prefer specific source systems
 *   <li>MERGE strategy for phones - union with max limit
 *   <li>MERGE with limit - oldest value dropped (FIFO)
 * </ol>
 */
class ConflictResolutionTest {

  private LatestUpdateResolver latestUpdateResolver;
  private TrustedSourceResolver trustedSourceResolver;
  private MostFrequentResolver mostFrequentResolver;
  private NonNullResolver nonNullResolver;
  private MergeResolver mergeResolver;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    latestUpdateResolver = new LatestUpdateResolver();
    trustedSourceResolver = new TrustedSourceResolver();
    mostFrequentResolver = new MostFrequentResolver();
    nonNullResolver = new NonNullResolver();
    mergeResolver = new MergeResolver();
    meterRegistry = new SimpleMeterRegistry();
  }

  // ========== Test 1: LATEST_UPDATE strategy ==========

  @Nested
  @DisplayName("Test 1: LATEST_UPDATE strategy")
  class LatestUpdateStrategyTest {

    @Test
    @DisplayName("Incoming value with later timestamp wins")
    void testIncomingValueWithLaterTimestampWins() {
      Instant baseTime = Instant.parse("2024-01-15T10:30:00Z");
      FieldConflict conflict =
          new FieldConflict(
              "email",
              "email@old.com",
              "email@new.com",
              baseTime,
              baseTime.plusSeconds(5),
              "CRM",
              "BANK");

      ResolutionConfig config = new ResolutionConfig(ConflictResolutionStrategy.LATEST_UPDATE);
      FieldResolution resolution = latestUpdateResolver.resolve(conflict, config);

      assertEquals("email@new.com", resolution.resolvedValue());
      assertTrue(resolution.valueChanged());
      assertTrue(resolution.resolutionReason().contains("BANK"));
      assertTrue(resolution.resolutionReason().contains("later timestamp"));
    }

    @Test
    @DisplayName("Current value with later timestamp is kept")
    void testCurrentValueWithLaterTimestampKept() {
      Instant baseTime = Instant.parse("2024-01-15T10:30:00Z");
      FieldConflict conflict =
          new FieldConflict(
              "email",
              "email@new.com",
              "email@old.com",
              baseTime.plusSeconds(5),
              baseTime,
              "BANK",
              "CRM");

      ResolutionConfig config = new ResolutionConfig(ConflictResolutionStrategy.LATEST_UPDATE);
      FieldResolution resolution = latestUpdateResolver.resolve(conflict, config);

      assertEquals("email@new.com", resolution.resolvedValue());
      assertFalse(resolution.valueChanged());
      assertTrue(resolution.resolutionReason().contains("Current value has later timestamp"));
    }

    @Test
    @DisplayName("Same timestamp prefers incoming value as tiebreaker")
    void testSameTimestampPrefersIncoming() {
      Instant sameTime = Instant.parse("2024-01-15T10:30:00Z");
      FieldConflict conflict =
          new FieldConflict(
              "email", "email@crm.com", "email@bank.com", sameTime, sameTime, "CRM", "BANK");

      ResolutionConfig config = new ResolutionConfig(ConflictResolutionStrategy.LATEST_UPDATE);
      FieldResolution resolution = latestUpdateResolver.resolve(conflict, config);

      assertEquals("email@bank.com", resolution.resolvedValue());
      assertTrue(resolution.valueChanged());
      assertTrue(resolution.resolutionReason().contains("Same timestamp"));
    }
  }

  // ========== Test 2: TRUSTED_SOURCE strategy ==========

  @Nested
  @DisplayName("Test 2: TRUSTED_SOURCE strategy")
  class TrustedSourceStrategyTest {

    @Test
    @DisplayName("Trusted source (BANK) wins over non-trusted (CRM)")
    void testTrustedSourceWinsOverNonTrusted() {
      FieldConflict conflict =
          new FieldConflict(
              "email",
              "email@crm.com",
              "email@bank.com",
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      ResolutionConfig config =
          new ResolutionConfig(
              ConflictResolutionStrategy.TRUSTED_SOURCE, Arrays.asList("BANK", "GOVERNMENT"), null);

      FieldResolution resolution = trustedSourceResolver.resolve(conflict, config);

      assertEquals("email@bank.com", resolution.resolvedValue());
      assertTrue(resolution.valueChanged());
      assertTrue(resolution.resolutionReason().contains("BANK is a trusted source"));
    }

    @Test
    @DisplayName("Current trusted source kept over incoming non-trusted")
    void testCurrentTrustedSourceKeptOverIncomingNonTrusted() {
      FieldConflict conflict =
          new FieldConflict(
              "email",
              "email@bank.com",
              "email@crm.com",
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "BANK",
              "CRM");

      ResolutionConfig config =
          new ResolutionConfig(
              ConflictResolutionStrategy.TRUSTED_SOURCE, Arrays.asList("BANK", "GOVERNMENT"), null);

      FieldResolution resolution = trustedSourceResolver.resolve(conflict, config);

      assertEquals("email@bank.com", resolution.resolvedValue());
      assertFalse(resolution.valueChanged());
      assertTrue(resolution.resolutionReason().contains("BANK is a trusted source"));
    }

    @Test
    @DisplayName("Both trusted sources: later timestamp wins")
    void testBothTrustedSourcesLaterTimestampWins() {
      FieldConflict conflict =
          new FieldConflict(
              "email",
              "email@bank.com",
              "email@gov.com",
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "BANK",
              "GOVERNMENT");

      ResolutionConfig config =
          new ResolutionConfig(
              ConflictResolutionStrategy.TRUSTED_SOURCE, Arrays.asList("BANK", "GOVERNMENT"), null);

      FieldResolution resolution = trustedSourceResolver.resolve(conflict, config);

      assertEquals("email@gov.com", resolution.resolvedValue());
      assertTrue(resolution.valueChanged());
      assertTrue(resolution.resolutionReason().contains("Both sources trusted"));
    }

    @Test
    @DisplayName("Neither trusted: fallback to latest update")
    void testNeitherTrustedFallsBackToLatestUpdate() {
      FieldConflict conflict =
          new FieldConflict(
              "email",
              "email@crm.com",
              "email@social.com",
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "SOCIAL");

      ResolutionConfig config =
          new ResolutionConfig(
              ConflictResolutionStrategy.TRUSTED_SOURCE, Arrays.asList("BANK", "GOVERNMENT"), null);

      FieldResolution resolution = trustedSourceResolver.resolve(conflict, config);

      assertEquals("email@social.com", resolution.resolvedValue());
      assertTrue(resolution.valueChanged());
      assertTrue(resolution.resolutionReason().contains("falling back to latest update"));
    }
  }

  // ========== Test 3: MERGE strategy for phones ==========

  @Nested
  @DisplayName("Test 3: MERGE strategy for phones")
  class MergeStrategyTest {

    @Test
    @DisplayName("Merge phone lists with UNION strategy")
    void testMergePhoneListsWithUnion() {
      List<String> currentPhones = Arrays.asList("+1-555-1111", "+1-555-2222");
      List<String> incomingPhones = Arrays.asList("+1-555-3333");

      FieldConflict conflict =
          new FieldConflict(
              "phone",
              currentPhones,
              incomingPhones,
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      MergeStrategy mergeStrategy = new MergeStrategy(MergeType.UNION, 5);
      ResolutionConfig config =
          new ResolutionConfig(ConflictResolutionStrategy.MERGE, null, mergeStrategy);

      FieldResolution resolution = mergeResolver.resolve(conflict, config);

      @SuppressWarnings("unchecked")
      List<String> merged = (List<String>) resolution.resolvedValue();
      assertEquals(3, merged.size());
      assertTrue(merged.contains("+1-555-1111"));
      assertTrue(merged.contains("+1-555-2222"));
      assertTrue(merged.contains("+1-555-3333"));
      assertTrue(resolution.valueChanged());
    }

    @Test
    @DisplayName("Merge removes duplicates")
    void testMergeRemovesDuplicates() {
      List<String> currentPhones = Arrays.asList("+1-555-1111", "+1-555-2222");
      List<String> incomingPhones = Arrays.asList("+1-555-2222", "+1-555-3333");

      FieldConflict conflict =
          new FieldConflict(
              "phone",
              currentPhones,
              incomingPhones,
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      MergeStrategy mergeStrategy = new MergeStrategy(MergeType.UNION, 5);
      ResolutionConfig config =
          new ResolutionConfig(ConflictResolutionStrategy.MERGE, null, mergeStrategy);

      FieldResolution resolution = mergeResolver.resolve(conflict, config);

      @SuppressWarnings("unchecked")
      List<String> merged = (List<String>) resolution.resolvedValue();
      assertEquals(3, merged.size(), "Should have 3 unique phones");
    }
  }

  // ========== Test 4: MERGE with limit ==========

  @Nested
  @DisplayName("Test 4: MERGE with limit")
  class MergeWithLimitTest {

    @Test
    @DisplayName("Drops oldest values when exceeding max (FIFO)")
    void testDropsOldestValuesWhenExceedingMax() {
      List<String> currentPhones =
          Arrays.asList("+1-555-1111", "+1-555-2222", "+1-555-3333", "+1-555-4444");
      List<String> incomingPhones = Arrays.asList("+1-555-5555");

      FieldConflict conflict =
          new FieldConflict(
              "phone",
              currentPhones,
              incomingPhones,
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      MergeStrategy mergeStrategy = new MergeStrategy(MergeType.UNION, 4);
      ResolutionConfig config =
          new ResolutionConfig(ConflictResolutionStrategy.MERGE, null, mergeStrategy);

      FieldResolution resolution = mergeResolver.resolve(conflict, config);

      @SuppressWarnings("unchecked")
      List<String> merged = (List<String>) resolution.resolvedValue();
      assertEquals(4, merged.size(), "Should have max 4 phones");
      // FIFO: drop oldest (first) values, keep newest
      assertTrue(merged.contains("+1-555-2222"));
      assertTrue(merged.contains("+1-555-3333"));
      assertTrue(merged.contains("+1-555-4444"));
      assertTrue(merged.contains("+1-555-5555"));
      assertFalse(merged.contains("+1-555-1111"), "Oldest should be dropped");
    }

    @Test
    @DisplayName("Merge with limit larger than total keeps all")
    void testMergeWithLimitLargerThanTotal() {
      List<String> currentPhones = Arrays.asList("+1-555-1111");
      List<String> incomingPhones = Arrays.asList("+1-555-2222");

      FieldConflict conflict =
          new FieldConflict(
              "phone",
              currentPhones,
              incomingPhones,
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      MergeStrategy mergeStrategy = new MergeStrategy(MergeType.UNION, 10);
      ResolutionConfig config =
          new ResolutionConfig(ConflictResolutionStrategy.MERGE, null, mergeStrategy);

      FieldResolution resolution = mergeResolver.resolve(conflict, config);

      @SuppressWarnings("unchecked")
      List<String> merged = (List<String>) resolution.resolvedValue();
      assertEquals(2, merged.size());
    }
  }

  // ========== Additional Strategy Tests ==========

  @Nested
  @DisplayName("MOST_FREQUENT strategy tests")
  class MostFrequentStrategyTest {

    @Test
    @DisplayName("Keeps current value as most frequent")
    void testKeepsCurrentValueAsMostFrequent() {
      FieldConflict conflict =
          new FieldConflict(
              "name",
              "John Doe",
              "J. Doe",
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      ResolutionConfig config = new ResolutionConfig(ConflictResolutionStrategy.MOST_FREQUENT);
      FieldResolution resolution = mostFrequentResolver.resolve(conflict, config);

      assertEquals("John Doe", resolution.resolvedValue());
      assertFalse(resolution.valueChanged());
      assertTrue(resolution.resolutionReason().contains("most frequent"));
    }
  }

  @Nested
  @DisplayName("NON_NULL strategy tests")
  class NonNullStrategyTest {

    @Test
    @DisplayName("Keeps non-null current value")
    void testKeepsNonNullCurrentValue() {
      FieldConflict conflict =
          new FieldConflict(
              "email",
              "john@crm.com",
              null,
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      ResolutionConfig config = new ResolutionConfig(ConflictResolutionStrategy.NON_NULL);
      FieldResolution resolution = nonNullResolver.resolve(conflict, config);

      assertEquals("john@crm.com", resolution.resolvedValue());
      assertFalse(resolution.valueChanged());
    }

    @Test
    @DisplayName("Uses incoming value when current is null")
    void testUsesIncomingWhenCurrentIsNull() {
      FieldConflict conflict =
          new FieldConflict(
              "email",
              null,
              "john@bank.com",
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      ResolutionConfig config = new ResolutionConfig(ConflictResolutionStrategy.NON_NULL);
      FieldResolution resolution = nonNullResolver.resolve(conflict, config);

      assertEquals("john@bank.com", resolution.resolvedValue());
      assertTrue(resolution.valueChanged());
    }

    @Test
    @DisplayName("Both non-null keeps first seen value")
    void testBothNonNullKeepsFirstSeen() {
      FieldConflict conflict =
          new FieldConflict(
              "email",
              "john@crm.com",
              "john@bank.com",
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      ResolutionConfig config = new ResolutionConfig(ConflictResolutionStrategy.NON_NULL);
      FieldResolution resolution = nonNullResolver.resolve(conflict, config);

      assertEquals("john@crm.com", resolution.resolvedValue());
      assertFalse(resolution.valueChanged());
      assertTrue(resolution.resolutionReason().contains("first seen"));
    }
  }

  // ========== Edge Cases ==========

  @Nested
  @DisplayName("Edge cases")
  class EdgeCasesTest {

    @Test
    @DisplayName("Identical values return unchanged")
    void testIdenticalValuesReturnUnchanged() {
      FieldConflict conflict =
          new FieldConflict(
              "email",
              "same@email.com",
              "same@email.com",
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      ResolutionConfig config = new ResolutionConfig(ConflictResolutionStrategy.LATEST_UPDATE);
      FieldResolution resolution = latestUpdateResolver.resolve(conflict, config);

      assertEquals("same@email.com", resolution.resolvedValue());
      assertFalse(resolution.valueChanged());
    }

    @Test
    @DisplayName("No conflict when one value is null")
    void testNoConflictWhenOneValueIsNull() {
      FieldConflict conflict =
          new FieldConflict(
              "email",
              "john@crm.com",
              null,
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      assertFalse(conflict.hasConflict());
    }

    @Test
    @DisplayName("Single value merge")
    void testSingleValueMerge() {
      FieldConflict conflict =
          new FieldConflict(
              "phone",
              "+1-555-1111",
              "+1-555-2222",
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      MergeStrategy mergeStrategy = new MergeStrategy(MergeType.UNION, 5);
      ResolutionConfig config =
          new ResolutionConfig(ConflictResolutionStrategy.MERGE, null, mergeStrategy);

      FieldResolution resolution = mergeResolver.resolve(conflict, config);

      @SuppressWarnings("unchecked")
      List<String> merged = (List<String>) resolution.resolvedValue();
      assertEquals(2, merged.size());
      assertTrue(merged.contains("+1-555-1111"));
      assertTrue(merged.contains("+1-555-2222"));
    }

    @Test
    @DisplayName("Empty lists merge")
    void testEmptyListsMerge() {
      FieldConflict conflict =
          new FieldConflict(
              "phone",
              Collections.emptyList(),
              Collections.emptyList(),
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      MergeStrategy mergeStrategy = new MergeStrategy(MergeType.UNION, 5);
      ResolutionConfig config =
          new ResolutionConfig(ConflictResolutionStrategy.MERGE, null, mergeStrategy);

      FieldResolution resolution = mergeResolver.resolve(conflict, config);

      @SuppressWarnings("unchecked")
      List<String> merged = (List<String>) resolution.resolvedValue();
      assertTrue(merged.isEmpty());
    }
  }

  // ========== ConflictResolutionService Integration Test ==========

  @Nested
  @DisplayName("ConflictResolutionService integration")
  class ConflictResolutionServiceTest {

    @Test
    @DisplayName("Service delegates to correct resolver and tracks metrics")
    void serviceDelegatesToCorrectResolverAndTracksMetrics() {
      ConflictResolutionConfig config = new ConflictResolutionConfig();
      config.setDefaultStrategy(ConflictResolutionStrategy.LATEST_UPDATE);

      ConflictResolutionService service =
          new ConflictResolutionService(
              new LatestUpdateResolver(),
              new TrustedSourceResolver(),
              new MostFrequentResolver(),
              new NonNullResolver(),
              new MergeResolver(),
              config,
              meterRegistry);

      FieldConflict conflict =
          new FieldConflict(
              "email",
              "old@email.com",
              "new@email.com",
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.parse("2024-01-15T10:30:05Z"),
              "CRM",
              "BANK");

      FieldResolution resolution = service.resolve(conflict);

      assertEquals("new@email.com", resolution.resolvedValue());
      assertTrue(resolution.valueChanged());

      // Verify metric was recorded
      double counterCount =
          meterRegistry.counter("conflicts_resolved_total", "strategy", "LATEST_UPDATE").count();
      assertEquals(1.0, counterCount, 0.01);
    }
  }
}
