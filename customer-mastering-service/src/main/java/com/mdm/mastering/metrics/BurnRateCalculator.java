/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.metrics;

/**
 * Calculate SLO burn rate and error budget remaining.
 *
 * <p>Burn Rate = Current Error Rate / Allowed Error Rate
 *
 * <p>Example: 5x burn rate = budget consumed 5x faster than normal
 *
 * <h2>Usage:</h2>
 *
 * <pre>{@code
 * BurnRateCalculator calculator = new BurnRateCalculator(99.9); // 99.9% SLO
 * double burnRate = calculator.calculateBurnRate(10000, 5); // 5 errors out of 10000
 * double budgetRemaining = calculator.calculateErrorBudgetRemaining(10000, 5);
 * }</pre>
 *
 * @see <a href="https://sre.google/sre-book/monitoring-distributed-systems/">Google SRE Book</a>
 */
public class BurnRateCalculator {

  private final double sloTarget; // e.g., 99.9 for 99.9% SLO

  /**
   * Create burn rate calculator for given SLO target.
   *
   * @param sloTarget SLO target as percentage (e.g., 99.9 for 99.9%)
   */
  public BurnRateCalculator(double sloTarget) {
    if (sloTarget <= 0 || sloTarget > 100) {
      throw new IllegalArgumentException("SLO target must be between 0 and 100: " + sloTarget);
    }
    this.sloTarget = sloTarget;
  }

  /**
   * Calculate burn rate from metrics.
   *
   * <p>Burn Rate = Current Error Rate / Allowed Error Rate
   *
   * <p>Interpretation:
   *
   * <ul>
   *   <li>1x = consuming budget at normal rate
   *   <li>2x = consuming budget 2x faster than allowed
   *   <li>10x = consuming budget 10x faster (critical)
   *   <li>0x = no errors, budget preserved
   * </ul>
   *
   * @param totalRequests total number of requests
   * @param failedRequests number of failed requests
   * @return burn rate multiplier (e.g., 5.0 = 5x burn rate)
   */
  public double calculateBurnRate(long totalRequests, long failedRequests) {
    if (totalRequests == 0) {
      return 0.0;
    }

    double currentErrorRate = (double) failedRequests / totalRequests;
    double maxErrorRate = 1.0 - (sloTarget / 100.0);

    if (maxErrorRate == 0) {
      return currentErrorRate > 0 ? Double.MAX_VALUE : 0.0;
    }

    return currentErrorRate / maxErrorRate;
  }

  /**
   * Calculate error budget remaining (percentage).
   *
   * <p>100% = full budget remaining
   *
   * <p>0% = budget exhausted (SLO breached)
   *
   * <p>Negative = already breached
   *
   * @param totalRequests total number of requests
   * @param failedRequests number of failed requests
   * @return error budget remaining (0-100%)
   */
  public double calculateErrorBudgetRemaining(long totalRequests, long failedRequests) {
    if (totalRequests == 0) {
      return 100.0;
    }

    double currentErrorRate = (double) failedRequests / totalRequests;
    double maxErrorRate = 1.0 - (sloTarget / 100.0);

    double remaining = ((maxErrorRate - currentErrorRate) / maxErrorRate) * 100.0;
    return Math.max(0.0, remaining);
  }

  /**
   * Get monthly error budget in minutes.
   *
   * <p>For 99.9% SLO: 0.1% × 30 days × 24 hours × 60 minutes = 43.2 minutes
   *
   * <p>For 99.99% SLO: 0.01% × 30 days × 24 hours × 60 minutes = 4.32 minutes
   *
   * @return error budget in minutes per 30-day month
   */
  public double getMonthlyErrorBudgetMinutes() {
    double maxErrorRate = 1.0 - (sloTarget / 100.0);
    return maxErrorRate * 30 * 24 * 60; // minutes per month
  }

  /**
   * Get monthly error budget in seconds.
   *
   * @return error budget in seconds per 30-day month
   */
  public double getMonthlyErrorBudgetSeconds() {
    return getMonthlyErrorBudgetMinutes() * 60;
  }

  /**
   * Get weekly error budget in minutes.
   *
   * @return error budget in minutes per 7-day week
   */
  public double getWeeklyErrorBudgetMinutes() {
    double maxErrorRate = 1.0 - (sloTarget / 100.0);
    return maxErrorRate * 7 * 24 * 60; // minutes per week
  }

  /**
   * Get daily error budget in minutes.
   *
   * @return error budget in minutes per day
   */
  public double getDailyErrorBudgetMinutes() {
    double maxErrorRate = 1.0 - (sloTarget / 100.0);
    return maxErrorRate * 24 * 60; // minutes per day
  }

  /**
   * Calculate required error rate to stay within SLO.
   *
   * @return maximum allowed error rate as percentage
   */
  public double getMaxAllowedErrorRate() {
    return (1.0 - (sloTarget / 100.0)) * 100.0;
  }

  /**
   * Check if current error rate is within SLO.
   *
   * @param totalRequests total number of requests
   * @param failedRequests number of failed requests
   * @return true if within SLO, false if breached
   */
  public boolean isWithinSlo(long totalRequests, long failedRequests) {
    return calculateErrorBudgetRemaining(totalRequests, failedRequests) > 0;
  }

  /**
   * Get SLO target.
   *
   * @return SLO target as percentage
   */
  public double getSloTarget() {
    return sloTarget;
  }

  /**
   * Get burn rate severity level.
   *
   * @param burnRate current burn rate
   * @return severity level (CRITICAL, HIGH, ELEVATED, NORMAL)
   */
  public static Severity getBurnRateSeverity(double burnRate) {
    if (burnRate >= 10) {
      return Severity.CRITICAL;
    } else if (burnRate >= 5) {
      return Severity.HIGH;
    } else if (burnRate >= 2) {
      return Severity.ELEVATED;
    } else {
      return Severity.NORMAL;
    }
  }

  /** Burn rate severity levels matching alert severity. */
  public enum Severity {
    CRITICAL("10x+ burn rate - Immediate action required"),
    HIGH("5x+ burn rate - Urgent attention needed"),
    ELEVATED("2x+ burn rate - Monitor closely"),
    NORMAL("Normal operations");

    private final String description;

    Severity(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }
}
