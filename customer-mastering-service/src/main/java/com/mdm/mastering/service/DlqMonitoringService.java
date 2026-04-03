/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import com.mdm.mastering.metrics.RetryAndDlqMetrics;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Monitors DLQ rate and triggers alerts when thresholds are exceeded.
 *
 * <p>Evaluation window and threshold are configurable via application.yml.
 * Alert channels are defined in configuration (email, prometheus, SMS).
 */
@Service
public class DlqMonitoringService {

  private static final Logger log = LoggerFactory.getLogger(DlqMonitoringService.class);

  private final RetryAndDlqMetrics metrics;
  private final boolean alertEnabled;
  private final double thresholdPercent;
  private final long evaluationWindowSeconds;
  private final List<String> alertChannels;

  private volatile Instant lastAlertTime = Instant.MIN;

  public DlqMonitoringService(
      RetryAndDlqMetrics metrics,
      @Value("${mdm.mastering.dlq.alert.enabled:true}") boolean alertEnabled,
      @Value("${mdm.mastering.dlq.alert.thresholdPercent:1.0}") double thresholdPercent,
      @Value("${mdm.mastering.dlq.alert.evaluationWindowSeconds:300}") long evaluationWindowSeconds,
      @Value("${mdm.mastering.dlq.alert.channels:email,prometheus}") List<String> alertChannels) {
    this.metrics = metrics;
    this.alertEnabled = alertEnabled;
    this.thresholdPercent = thresholdPercent;
    this.evaluationWindowSeconds = evaluationWindowSeconds;
    this.alertChannels = alertChannels;
  }

  /**
   * Periodically evaluates the DLQ rate and triggers alerts if threshold is exceeded.
   */
  @Scheduled(fixedRateString = "${mdm.mastering.dlq.alert.evaluationWindowSeconds:300}000")
  public void evaluateDlqRate() {
    if (!alertEnabled) {
      return;
    }

    double dlqRate = metrics.getDlqRate();

    if (dlqRate > thresholdPercent) {
      Instant now = Instant.now();
      long secondsSinceLastAlert = java.time.Duration.between(lastAlertTime, now).getSeconds();

      if (secondsSinceLastAlert >= evaluationWindowSeconds) {
        triggerAlert(dlqRate);
        lastAlertTime = now;
      }
    }
  }

  private void triggerAlert(double dlqRate) {
    String message = String.format(
        "ALERT: DLQ rate (%.2f%%) exceeds threshold (%.2f%%). DLQ messages: %d, Total processed: %d",
        dlqRate, thresholdPercent, metrics.getTotalDlqMessages(), metrics.getTotalEventsProcessed());

    for (String channel : alertChannels) {
      switch (channel.toLowerCase()) {
        case "email" -> logAlertToChannel("EMAIL", message);
        case "prometheus" -> logAlertToChannel("PROMETHEUS", message);
        case "sms" -> logAlertToChannel("SMS", message);
        default -> log.warn("Unknown alert channel: {}", channel);
      }
    }
  }

  private void logAlertToChannel(String channel, String message) {
    log.error("[DLQ ALERT][{}] {}", channel, message);
  }
}
