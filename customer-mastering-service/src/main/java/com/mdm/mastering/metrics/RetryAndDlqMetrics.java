/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.metrics;

import com.mdm.mastering.exception.ErrorType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Metrics for retry and DLQ monitoring.
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@code retry_attempts_total} - total retry attempts by error type</li>
 *   <li>{@code dlq_messages_total} - total messages sent to DLQ by error type</li>
 *   <li>{@code dlq_rate_percent} - current DLQ rate as percentage of total events</li>
 * </ul>
 */
@Component
public class RetryAndDlqMetrics {

  private final Counter retryAttemptsTransient;
  private final Counter retryAttemptsPermanent;
  private final Counter retryAttemptsBusiness;
  private final Counter dlqMessagesTransient;
  private final Counter dlqMessagesPermanent;
  private final Counter dlqMessagesBusiness;

  private final AtomicLong totalEventsProcessed = new AtomicLong(0);
  private final AtomicLong totalDlqMessages = new AtomicLong(0);

  public RetryAndDlqMetrics(MeterRegistry meterRegistry) {
    this.retryAttemptsTransient = Counter.builder("retry_attempts_total")
        .description("Total number of retry attempts")
        .tag("error_type", "TRANSIENT")
        .register(meterRegistry);

    this.retryAttemptsPermanent = Counter.builder("retry_attempts_total")
        .description("Total number of retry attempts")
        .tag("error_type", "PERMANENT")
        .register(meterRegistry);

    this.retryAttemptsBusiness = Counter.builder("retry_attempts_total")
        .description("Total number of retry attempts")
        .tag("error_type", "BUSINESS")
        .register(meterRegistry);

    this.dlqMessagesTransient = Counter.builder("dlq_messages_total")
        .description("Total messages sent to DLQ")
        .tag("error_type", "TRANSIENT")
        .register(meterRegistry);

    this.dlqMessagesPermanent = Counter.builder("dlq_messages_total")
        .description("Total messages sent to DLQ")
        .tag("error_type", "PERMANENT")
        .register(meterRegistry);

    this.dlqMessagesBusiness = Counter.builder("dlq_messages_total")
        .description("Total messages sent to DLQ")
        .tag("error_type", "BUSINESS")
        .register(meterRegistry);
  }

  /** Record a retry attempt for the given error type. */
  public void recordRetry(ErrorType errorType) {
    switch (errorType) {
      case TRANSIENT -> retryAttemptsTransient.increment();
      case PERMANENT -> retryAttemptsPermanent.increment();
      case BUSINESS -> retryAttemptsBusiness.increment();
    }
  }

  /** Record a message sent to DLQ. */
  public void recordDlqMessage(ErrorType errorType) {
    totalDlqMessages.incrementAndGet();
    switch (errorType) {
      case TRANSIENT -> dlqMessagesTransient.increment();
      case PERMANENT -> dlqMessagesPermanent.increment();
      case BUSINESS -> dlqMessagesBusiness.increment();
    }
  }

  /** Record total events processed for rate calculation. */
  public void recordEventProcessed() {
    totalEventsProcessed.incrementAndGet();
  }

  /** Get current DLQ rate as percentage. */
  public double getDlqRate() {
    long total = totalEventsProcessed.get();
    if (total == 0) {
      return 0.0;
    }
    return (totalDlqMessages.get() * 100.0) / total;
  }

  /** Get total DLQ messages. */
  public long getTotalDlqMessages() {
    return totalDlqMessages.get();
  }

  /** Get total events processed. */
  public long getTotalEventsProcessed() {
    return totalEventsProcessed.get();
  }
}
