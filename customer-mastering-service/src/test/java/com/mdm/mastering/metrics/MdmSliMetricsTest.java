/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MDM SLI Metrics.
 *
 * <p>Tests cover: - Metric registration - Metric recording - Metric calculations (rates, counts)
 */
class MdmSliMetricsTest {

  private MeterRegistry meterRegistry;
  private MdmSliMetrics metrics;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    metrics = new MdmSliMetrics(meterRegistry);
  }

  @Test
  @DisplayName("Should register all metrics on construction")
  void shouldRegisterAllMetrics() {
    // Verify metrics are registered by checking they can be found
    assertNotNull(meterRegistry.find("mdm.event_processing_latency_seconds").timer());
    assertNotNull(meterRegistry.find("mdm.deduplication_lookup_latency_seconds").timer());
    assertNotNull(meterRegistry.find("mdm.duplicates_detected_total").counter());
    assertNotNull(meterRegistry.find("mdm.golden_records_created_total").counter());
    assertNotNull(meterRegistry.find("mdm.golden_records_updated_total").counter());
    assertNotNull(meterRegistry.find("mdm.processing_errors_total").counter());
    assertNotNull(meterRegistry.find("mdm.events_processed_total").functionCounter());
  }

  @Test
  @DisplayName("Should record successful processing")
  void shouldRecordSuccessfulProcessing() {
    boolean result =
        metrics.recordProcessing(
            () -> {
              // Simulate processing
            });

    assertTrue(result);
    assertEquals(1, metrics.getTotalEventsProcessed());
    assertEquals(0, metrics.getTotalErrors());
  }

  @Test
  @DisplayName("Should record failed processing")
  void shouldRecordFailedProcessing() {
    assertThrows(
        RuntimeException.class,
        () -> {
          metrics.recordProcessing(
              () -> {
                throw new RuntimeException("Processing failed");
              });
        });

    assertEquals(1, metrics.getTotalEventsProcessed());
    assertEquals(1, metrics.getTotalErrors());
  }

  @Test
  @DisplayName("Should record duplicate detection")
  void shouldRecordDuplicate() {
    metrics.recordDuplicate();

    assertEquals(1, metrics.getTotalDuplicates());
  }

  @Test
  @DisplayName("Should record golden record creation")
  void shouldRecordGoldenRecordCreated() {
    metrics.recordGoldenRecordCreated();

    Counter counter = meterRegistry.get("mdm.golden_records_created_total").counter();
    assertEquals(1, counter.count());
  }

  @Test
  @DisplayName("Should record golden record update")
  void shouldRecordGoldenRecordUpdated() {
    metrics.recordGoldenRecordUpdated();

    Counter counter = meterRegistry.get("mdm.golden_records_updated_total").counter();
    assertEquals(1, counter.count());
  }

  @Test
  @DisplayName("Should calculate duplicate rate correctly")
  void shouldCalculateDuplicateRate() {
    // Process 10 events
    for (int i = 0; i < 10; i++) {
      metrics.recordProcessing(() -> {});
    }

    // 5 are duplicates
    for (int i = 0; i < 5; i++) {
      metrics.recordDuplicate();
    }

    assertEquals(50.0, metrics.getDuplicateRate());
  }

  @Test
  @DisplayName("Should return 0 duplicate rate when no events processed")
  void shouldReturnZeroDuplicateRateWhenNoEvents() {
    assertEquals(0.0, metrics.getDuplicateRate());
  }

  @Test
  @DisplayName("Should calculate error rate correctly")
  void shouldCalculateErrorRate() {
    // Process 10 events, 2 fail
    for (int i = 0; i < 8; i++) {
      metrics.recordProcessing(() -> {});
    }

    for (int i = 0; i < 2; i++) {
      assertThrows(
          RuntimeException.class,
          () -> {
            metrics.recordProcessing(
                () -> {
                  throw new RuntimeException("Error");
                });
          });
    }

    assertEquals(20.0, metrics.getErrorRate());
  }

  @Test
  @DisplayName("Should return 0 error rate when no events processed")
  void shouldReturnZeroErrorRateWhenNoEvents() {
    assertEquals(0.0, metrics.getErrorRate());
  }

  @Test
  @DisplayName("Should record deduplication lookup with timing")
  void shouldRecordDeduplicationLookup() {
    String result = metrics.recordDeduplicationLookup(() -> "test-result");

    assertEquals("test-result", result);
    assertNotNull(meterRegistry.find("mdm.deduplication_lookup_latency_seconds").timer());
  }

  @Test
  @DisplayName("Should handle null lookup result")
  void shouldHandleNullLookupResult() {
    String result = metrics.recordDeduplicationLookup(() -> null);

    assertNull(result);
  }
}
