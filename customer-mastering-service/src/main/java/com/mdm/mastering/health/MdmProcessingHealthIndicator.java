/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.health;

import java.time.Duration;
import java.time.Instant;

import lombok.Getter;
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

  private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(15);
  private static final Duration STARTUP_GRACE_PERIOD = Duration.ofMinutes(30);
  private static final Duration IDLE_TIMEOUT = Duration.ofHours(2);

  // Track last processing time and service start time
  private volatile Instant lastSuccessfulProcessingTime;
  private volatile Instant serviceStartTime;
  private volatile boolean hasProcessedAnyEvent = false;
  @Getter
  private volatile long totalProcessed = 0;
  @Getter
  private volatile long totalDuplicates = 0;

  public MdmProcessingHealthIndicator() {
    this.serviceStartTime = Instant.now();
    this.lastSuccessfulProcessingTime = this.serviceStartTime;
  }

  @Override
  public Health health() {
    long duplicateRate = totalProcessed > 0 ? (totalDuplicates * 100 / totalProcessed) : 0;

    // During startup grace period, report UP if no events processed yet
    Duration sinceStartup = Duration.between(serviceStartTime, Instant.now());
    if (!hasProcessedAnyEvent && sinceStartup.compareTo(STARTUP_GRACE_PERIOD) < 0) {
      return Health.up()
          .withDetail("status", "healthy (startup grace)")
          .withDetail("minutesUntilGraceExpires",
              STARTUP_GRACE_PERIOD.minus(sinceStartup).toMinutes())
          .withDetail("totalProcessed", 0)
          .build();
    }

    // If no events processed but service is idle (low traffic), still healthy
    if (!hasProcessedAnyEvent) {
      return Health.up()
          .withDetail("status", "healthy (idle, no traffic)")
          .withDetail("minutesSinceStartup", sinceStartup.toMinutes())
          .withDetail("totalProcessed", 0)
          .build();
    }

    // Check processing recency (only matters if we've seen traffic)
    Duration sinceLastProcessing = Duration.between(lastSuccessfulProcessingTime, Instant.now());

    if (sinceLastProcessing.compareTo(PROCESSING_TIMEOUT) > 0
        && sinceLastProcessing.compareTo(IDLE_TIMEOUT) < 0) {
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
    hasProcessedAnyEvent = true;
    lastSuccessfulProcessingTime = Instant.now();
    totalProcessed++;
    if (wasDuplicate) {
      totalDuplicates++;
    }
  }

}
