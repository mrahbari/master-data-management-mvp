/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.endpoint;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import com.mdm.mastering.metrics.BurnRateCalculator;
import com.mdm.mastering.metrics.MdmSliMetrics;

/**
 * Actuator endpoint for SLO status.
 *
 * <p>Exposes: {@code /actuator/slo-status}
 *
 * <p>Response includes:
 *
 * <ul>
 *   <li>Current availability percentage
 *   <li>Error budget remaining (%)
 *   <li>Burn rate multiplier
 *   <li>Monthly error budget (minutes)
 *   <li>SLO compliance status
 *   <li>Total events processed
 *   <li>Total errors
 *   <li>Duplicate rate
 * </ul>
 *
 * <h2>Example Response:</h2>
 *
 * <pre>{@code
 * {
 *   "availability": "99.9500%",
 *   "errorBudgetRemaining": "67.50%",
 *   "burnRate": "1.20x",
 *   "monthlyErrorBudgetMinutes": 43.2,
 *   "sloMet": true,
 *   "status": "HEALTHY",
 *   "totalEventsProcessed": 15420,
 *   "totalErrors": 8,
 *   "duplicateRate": "27.35%",
 *   "timestamp": "2024-01-15T10:30:00Z"
 * }
 * }</pre>
 *
 * @see <a href="https://sre.google/sre-book/service-level-objectives/">Google SRE: SLOs</a>
 */
@Component
@Endpoint(id = "slo-status")
public class SloEndpoint {

  private final MdmSliMetrics sloMetrics;
  private final BurnRateCalculator burnRateCalculator;

  /** Create SLO endpoint with default 99.9% SLO target. */
  public SloEndpoint(MdmSliMetrics sloMetrics) {
    this.sloMetrics = sloMetrics;
    this.burnRateCalculator = new BurnRateCalculator(99.9); // 99.9% SLO
  }

  /**
   * Get current SLO status.
   *
   * @return SLO status map
   */
  @ReadOperation
  public Map<String, Object> sloStatus() {
    Map<String, Object> result = new HashMap<>();

    // Current availability
    double availability = 100.0 - sloMetrics.getErrorRate();
    result.put("availability", String.format("%.4f%%", availability));

    // Error budget
    double errorBudgetRemaining =
        burnRateCalculator.calculateErrorBudgetRemaining(
            sloMetrics.getTotalEventsProcessed(), sloMetrics.getTotalErrors());
    result.put("errorBudgetRemaining", String.format("%.2f%%", errorBudgetRemaining));

    // Burn rate
    double burnRate =
        burnRateCalculator.calculateBurnRate(
            sloMetrics.getTotalEventsProcessed(), sloMetrics.getTotalErrors());
    result.put("burnRate", String.format("%.2fx", burnRate));

    // Burn rate severity
    BurnRateCalculator.Severity severity = BurnRateCalculator.getBurnRateSeverity(burnRate);
    result.put("burnRateSeverity", severity.name());
    result.put("burnRateDescription", severity.getDescription());

    // Monthly budget
    double monthlyBudgetMinutes = burnRateCalculator.getMonthlyErrorBudgetMinutes();
    result.put("monthlyErrorBudgetMinutes", monthlyBudgetMinutes);

    // Weekly and daily budgets
    result.put("weeklyErrorBudgetMinutes", burnRateCalculator.getWeeklyErrorBudgetMinutes());
    result.put("dailyErrorBudgetMinutes", burnRateCalculator.getDailyErrorBudgetMinutes());

    // Max allowed error rate
    result.put(
        "maxAllowedErrorRate",
        String.format("%.4f%%", burnRateCalculator.getMaxAllowedErrorRate()));

    // SLO status
    boolean sloMet = errorBudgetRemaining > 0;
    result.put("sloMet", sloMet);
    result.put("status", sloMet ? "HEALTHY" : "DEGRADED");

    // Additional metrics
    result.put("totalEventsProcessed", sloMetrics.getTotalEventsProcessed());
    result.put("totalErrors", sloMetrics.getTotalErrors());
    result.put("duplicateRate", String.format("%.2f%%", sloMetrics.getDuplicateRate()));

    // Timestamp
    result.put("timestamp", Instant.now().toString());

    // SLO target
    result.put("sloTarget", String.format("%.2f%%", burnRateCalculator.getSloTarget()));

    return result;
  }
}
