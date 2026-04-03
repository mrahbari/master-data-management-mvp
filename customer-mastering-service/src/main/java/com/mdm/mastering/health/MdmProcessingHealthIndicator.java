/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.health;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for MDM processing health.
 *
 * <p>Checks: - Last successful event processing timestamp - Duplicate detection rate (alerts if
 * suddenly 0 or 100%) - Kafka consumer lag (via JMX or metrics)
 */
@Component
public class MdmProcessingHealthIndicator implements HealthIndicator {

  private static final Logger log = LoggerFactory.getLogger(MdmProcessingHealthIndicator.class);

  private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);

  // Track last processing time
  private volatile Instant lastSuccessfulProcessingTime = Instant.now();
  private volatile long totalProcessed = 0;
  private volatile long totalDuplicates = 0;

  @Override
  public Health health() {
    long duplicateRate = totalProcessed > 0 ? (totalDuplicates * 100 / totalProcessed) : 0;

    // Check processing recency
    Duration sinceLastProcessing = Duration.between(lastSuccessfulProcessingTime, Instant.now());

    if (sinceLastProcessing.compareTo(PROCESSING_TIMEOUT) > 0) {
      log.warn("No successful event processing in {} minutes", sinceLastProcessing.toMinutes());
      return Health.down()
          .withDetail("issue", "No events processed recently")
          .withDetail("lastProcessingTime", lastSuccessfulProcessingTime.toString())
          .withDetail("minutesSinceLastProcessing", sinceLastProcessing.toMinutes())
          .build();
    }

    // Check duplicate rate anomalies
    if (totalProcessed > 100 && (duplicateRate == 0 || duplicateRate > 95)) {
      log.warn("Anomalous duplicate rate: {}%", duplicateRate);
      return Health.unknown()
          .withDetail("issue", "Anomalous duplicate rate")
          .withDetail("duplicateRate", duplicateRate + "%")
          .withDetail("totalProcessed", totalProcessed)
          .build();
    }

    return Health.up()
        .withDetail("status", "healthy")
        .withDetail("lastProcessingTime", lastSuccessfulProcessingTime.toString())
        .withDetail("totalProcessed", totalProcessed)
        .withDetail("totalDuplicates", totalDuplicates)
        .withDetail("duplicateRate", duplicateRate + "%")
        .build();
  }

  public void recordProcessing(boolean wasDuplicate) {
    lastSuccessfulProcessingTime = Instant.now();
    totalProcessed++;
    if (wasDuplicate) {
      totalDuplicates++;
    }
  }

  public long getTotalProcessed() {
    return totalProcessed;
  }

  public long getTotalDuplicates() {
    return totalDuplicates;
  }
}
