/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Centralized SLI metrics registry for MDM processing.
 *
 * <p>This component encapsulates all Service Level Indicator (SLI) metrics, providing a clean API
 * for services to record observations.
 *
 * <p>SLIs tracked: - Event processing latency (SLO: 99% < 100ms) - Deduplication lookup latency
 * (SLO: 99% < 10ms) - Duplicate detection count - Golden record creation/update count - Processing
 * errors (SLO: error rate < 0.1%) - Event throughput
 */
@Component
public class MdmSliMetrics {

  // SLI: Event processing latency (Timer)
  private final Timer eventProcessingTimer;

  // SLI: Deduplication lookup latency (Timer)
  private final Timer deduplicationLookupTimer;

  // SLI: Duplicate detection (Counter)
  private final Counter duplicatesDetectedCounter;

  // SLI: Golden record creation (Counter)
  private final Counter goldenRecordsCreatedCounter;

  // SLI: Golden record update (Counter)
  private final Counter goldenRecordsUpdatedCounter;

  // SLI: Processing errors (Counter)
  private final Counter processingErrorsCounter;

  // Out-of-order event handling: stale events detected
  private final Counter staleEventsTotalCounter;

  // Out-of-order event handling: optimistic lock failures
  private final Counter optimisticLockFailuresTotalCounter;

  // SLI: Throughput tracking (AtomicLong for thread-safety)
  private final AtomicLong totalEventsProcessed = new AtomicLong(0);
  private final FunctionCounter eventsProcessedCounter;

  public MdmSliMetrics(MeterRegistry meterRegistry) {
    // SLI-001: Event processing latency (SLO: 99% < 100ms)
    this.eventProcessingTimer =
        Timer.builder("mdm.event_processing_latency_seconds")
            .description("End-to-end event processing time (SLO: 99% < 100ms)")
            .serviceLevelObjectives(
                Duration.ofMillis(10),
                Duration.ofMillis(25),
                Duration.ofMillis(50),
                Duration.ofMillis(100),
                Duration.ofMillis(250),
                Duration.ofMillis(500),
                Duration.ofSeconds(1))
            .publishPercentileHistogram(true)
            .register(meterRegistry);

    // SLI-002: Deduplication lookup latency (SLO: 99% < 10ms)
    this.deduplicationLookupTimer =
        Timer.builder("mdm.deduplication_lookup_latency_seconds")
            .description("Database lookup time for deduplication (SLO: 99% < 10ms)")
            .serviceLevelObjectives(
                Duration.ofMillis(1),
                Duration.ofMillis(5),
                Duration.ofMillis(10),
                Duration.ofMillis(25),
                Duration.ofMillis(50))
            .publishPercentileHistogram(true)
            .register(meterRegistry);

    // SLI-003: Duplicate detection
    this.duplicatesDetectedCounter =
        Counter.builder("mdm.duplicates_detected_total")
            .description("Total duplicate customer records detected")
            .register(meterRegistry);

    // SLI-004: Golden record creation
    this.goldenRecordsCreatedCounter =
        Counter.builder("mdm.golden_records_created_total")
            .description("Total new golden records created")
            .register(meterRegistry);

    // SLI-005: Golden record update
    this.goldenRecordsUpdatedCounter =
        Counter.builder("mdm.golden_records_updated_total")
            .description("Total golden records updated")
            .register(meterRegistry);

    // SLI-006: Processing errors (SLO: error rate < 0.1%)
    this.processingErrorsCounter =
        Counter.builder("mdm.processing_errors_total")
            .description("Total event processing errors (SLO: error rate < 0.1%)")
            .register(meterRegistry);

    // Out-of-order: stale events total
    this.staleEventsTotalCounter =
        Counter.builder("stale_events_total")
            .description("Total stale/out-of-order events detected and acknowledged")
            .register(meterRegistry);

    // Out-of-order: optimistic lock failures total
    this.optimisticLockFailuresTotalCounter =
        Counter.builder("optimistic_lock_failures_total")
            .description("Total optimistic locking contention failures during retries")
            .register(meterRegistry);

    // SLI-007: Throughput
    this.eventsProcessedCounter =
        FunctionCounter.builder("mdm.events_processed_total", totalEventsProcessed, AtomicLong::get)
            .description("Total events processed (throughput SLI)")
            .register(meterRegistry);
  }

  /**
   * Record event processing with timing.
   *
   * @param processingTask the processing logic to time
   * @return true if processing succeeded, false otherwise
   */
  public boolean recordProcessing(Runnable processingTask) {
    try {
      eventProcessingTimer.record(processingTask);
      totalEventsProcessed.incrementAndGet();
      return true;
    } catch (Exception ex) {
      processingErrorsCounter.increment();
      totalEventsProcessed.incrementAndGet();
      throw ex;
    }
  }

  /**
   * Record deduplication lookup with timing.
   *
   * @param lookupTask the lookup logic to time
   * @param <T> the return type
   * @return the lookup result
   */
  public <T> T recordDeduplicationLookup(java.util.function.Supplier<T> lookupTask) {
    return deduplicationLookupTimer.record(lookupTask);
  }

  /** Record a duplicate detection. */
  public void recordDuplicate() {
    duplicatesDetectedCounter.increment();
  }

  /** Record a new golden record creation. */
  public void recordGoldenRecordCreated() {
    goldenRecordsCreatedCounter.increment();
  }

  /** Record a golden record update. */
  public void recordGoldenRecordUpdated() {
    goldenRecordsUpdatedCounter.increment();
  }

  /** Record a processing error (if not already recorded by recordProcessing). */
  public void recordError() {
    processingErrorsCounter.increment();
  }

  /** Record a stale event that was detected and acknowledged. */
  public void recordStaleEvent() {
    staleEventsTotalCounter.increment();
  }

  /** Record an optimistic lock contention failure. */
  public void recordOptimisticLockFailure() {
    optimisticLockFailuresTotalCounter.increment();
  }

  /**
   * Get current throughput (events processed).
   *
   * @return total events processed
   */
  public long getTotalEventsProcessed() {
    return totalEventsProcessed.get();
  }

  /**
   * Get current duplicate count.
   *
   * @return total duplicates detected
   */
  public long getTotalDuplicates() {
    return (long) duplicatesDetectedCounter.count();
  }

  /**
   * Get current error count.
   *
   * @return total errors
   */
  public long getTotalErrors() {
    return (long) processingErrorsCounter.count();
  }

  /**
   * Calculate current duplicate rate.
   *
   * @return duplicate rate as percentage (0-100)
   */
  public double getDuplicateRate() {
    long total = totalEventsProcessed.get();
    if (total == 0) {
      return 0.0;
    }
    return (getTotalDuplicates() * 100.0) / total;
  }

  /**
   * Calculate current error rate.
   *
   * @return error rate as percentage (0-100)
   */
  public double getErrorRate() {
    long total = totalEventsProcessed.get();
    if (total == 0) {
      return 0.0;
    }
    return (getTotalErrors() * 100.0) / total;
  }
}
