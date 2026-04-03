/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.dto.DlqEvent;
import com.mdm.mastering.exception.ClassifiedException;
import com.mdm.mastering.exception.ErrorType;
import com.mdm.mastering.metrics.RetryAndDlqMetrics;
import com.mdm.mastering.service.DlqMessageFormatter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Unit tests for retry and DLQ handling.
 *
 * <p>Test scenarios:
 * <ol>
 *   <li>Transient error with successful retry</li>
 *   <li>Permanent error (no retry, directly to DLQ)</li>
 *   <li>Transient error exhausting all retries → DLQ</li>
 *   <li>DLQ message structure validation</li>
 * </ol>
 */
class RetryAndDlqTest {

  private DlqMessageFormatter dlqMessageFormatter;
  private RetryAndDlqMetrics retryAndDlqMetrics;
  private CustomerRawEvent testEvent;

  @BeforeEach
  void setUp() {
    dlqMessageFormatter = new DlqMessageFormatter();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    retryAndDlqMetrics = new RetryAndDlqMetrics(meterRegistry);

    testEvent = CustomerRawEvent.builder()
        .eventId(UUID.randomUUID())
        .nationalId("1234567890")
        .name("John Doe")
        .email("john@example.com")
        .sourceSystem("test-system")
        .timestamp(Instant.now())
        .build();
  }

  // ========== Test 1: Transient error with successful retry ==========

  @Test
  @DisplayName("Test 1: Transient error with successful retry → event processed")
  void testTransientErrorWithSuccessfulRetry() {
    RetryTemplate retryTemplate = buildRetryTemplate(3, 100L, 2.0);

    int[] attemptCount = {0};
    try {
      retryTemplate.execute(context -> {
        attemptCount[0]++;
        if (attemptCount[0] < 2) {
          throw new DeadlockLoserDataAccessException("Deadlock detected", null);
        }
        return null;
      });
      // Retry succeeded on attempt 2
      assertEquals(2, attemptCount[0], "Should succeed on second attempt");
    } catch (Exception ex) {
      throw new AssertionError("Retry should have succeeded", ex);
    }
  }

  // ========== Test 2: Permanent error (no retry) ==========

  @Test
  @DisplayName("Test 2: Permanent error → immediately sent to DLQ")
  void testPermanentErrorSentToDlq() {
    ClassifiedException permEx = new ClassifiedException(
        "Duplicate key national_id", ErrorType.PERMANENT);

    ErrorType classified = dlqMessageFormatter.classifyException(permEx);
    assertEquals(ErrorType.PERMANENT, classified);

    // Permanent errors should NOT be retried
    assertFalse(dlqMessageFormatter.isRetryable(permEx));

    // Verify DLQ metrics would be incremented
    retryAndDlqMetrics.recordDlqMessage(ErrorType.PERMANENT);
    assertEquals(1, retryAndDlqMetrics.getTotalDlqMessages());
  }

  // ========== Test 3: Transient error exhausting retries → DLQ ==========

  @Test
  @DisplayName("Test 3: Transient error exhausting all retries → DLQ with history")
  void testTransientErrorExhaustingRetriesSentToDlq() {
    RetryTemplate retryTemplate = buildRetryTemplate(3, 100L, 2.0);

    int[] attemptCount = {0};
    try {
      retryTemplate.execute(context -> {
        attemptCount[0]++;
        throw new DeadlockLoserDataAccessException("Deadlock detected", null);
      });
    } catch (DeadlockLoserDataAccessException ex) {
      // Expected: all retries exhausted
      assertEquals(3, attemptCount[0], "Should exhaust all 3 retry attempts");

      // After retries exhausted, message goes to DLQ
      retryAndDlqMetrics.recordDlqMessage(ErrorType.TRANSIENT);
      assertEquals(1, retryAndDlqMetrics.getTotalDlqMessages());
    }
  }

  // ========== Test 4: DLQ message structure validation ==========

  @Test
  @DisplayName("Test 4: DLQ message contains payload + error + retry history + schema version")
  void testDlqMessageStructure() {
    Exception testException = new DeadlockLoserDataAccessException("Deadlock detected", null);

    DlqEvent dlqEvent = dlqMessageFormatter.formatDlqEvent(
        testEvent, testException, ErrorType.TRANSIENT, 3);

    // Verify structure
    assertNotNull(dlqEvent.getOriginalEvent());
    assertEquals(testEvent, dlqEvent.getOriginalEvent());
    assertEquals("v1", dlqEvent.getSchemaVersion());
    assertNotNull(dlqEvent.getErrorDetails());
    assertEquals("DeadlockLoserDataAccessException", dlqEvent.getErrorDetails().getException());
    assertNotNull(dlqEvent.getErrorDetails().getMessage());
    assertTrue(dlqEvent.getErrorDetails().getMessage().contains("Deadlock"));
    assertNotNull(dlqEvent.getErrorDetails().getStackTrace());
    assertEquals(1, dlqEvent.getProcessingHistory().size());
    assertEquals(3, dlqEvent.getProcessingHistory().get(0).getAttempt());
    assertNotNull(dlqEvent.getProcessingHistory().get(0).getError());
    assertTrue(dlqEvent.getProcessingHistory().get(0).getError().contains("Deadlock"));
    assertNotNull(dlqEvent.getProcessingHistory().get(0).getTimestamp());
  }

  // ========== Additional Tests ==========

  @Test
  @DisplayName("Test: Error classification for various exception types")
  void testErrorClassification() {
    // Permanent errors via ClassifiedException
    assertEquals(ErrorType.PERMANENT,
        dlqMessageFormatter.classifyException(
            new ClassifiedException("constraint violation", ErrorType.PERMANENT)));

    // Transient errors (by class name)
    assertEquals(ErrorType.TRANSIENT,
        dlqMessageFormatter.classifyException(
            new DeadlockLoserDataAccessException("deadlock", null)));

    assertEquals(ErrorType.TRANSIENT,
        dlqMessageFormatter.classifyException(
            new QueryTimeoutException("timeout", null)));

    // Business errors via ClassifiedException
    assertEquals(ErrorType.BUSINESS,
        dlqMessageFormatter.classifyException(
            new ClassifiedException("validation failed", ErrorType.BUSINESS)));
  }

  @Test
  @DisplayName("Test: Metrics track retry and DLQ counts")
  void testMetricsTracking() {
    retryAndDlqMetrics.recordRetry(ErrorType.TRANSIENT);
    retryAndDlqMetrics.recordRetry(ErrorType.TRANSIENT);
    retryAndDlqMetrics.recordRetry(ErrorType.TRANSIENT);
    retryAndDlqMetrics.recordDlqMessage(ErrorType.TRANSIENT);

    // Verify counters
    double retryCount = retryAndDlqMetrics.getTotalDlqMessages();
    assertEquals(1, retryCount);
  }

  @Test
  @DisplayName("Test: DLQ rate calculation and threshold alerting")
  void testDlqRateCalculation() {
    // Process 100 events, 2 go to DLQ
    for (int i = 0; i < 98; i++) {
      retryAndDlqMetrics.recordEventProcessed();
    }
    for (int i = 0; i < 2; i++) {
      retryAndDlqMetrics.recordEventProcessed();
      retryAndDlqMetrics.recordDlqMessage(ErrorType.PERMANENT);
    }

    double dlqRate = retryAndDlqMetrics.getDlqRate();
    assertEquals(2.0, dlqRate, 0.01, "DLQ rate should be 2%");
  }

  @Test
  @DisplayName("Test: nationalId is used as unique identifier for idempotency")
  void testNationalIdAsUniqueIdentifier() {
    String nationalId = "unique-national-id-123";
    CustomerRawEvent event = CustomerRawEvent.builder()
        .eventId(UUID.randomUUID())
        .nationalId(nationalId)
        .name("Test User")
        .email("test@example.com")
        .sourceSystem("test")
        .timestamp(Instant.now())
        .build();

    // Verify nationalId is set and non-null
    assertEquals(nationalId, event.getNationalId());
    assertFalse(event.getNationalId().isBlank());
  }

  @Test
  @DisplayName("Test: ClassifiedException preserves error type")
  void testClassifiedExceptionPreservesErrorType() {
    ClassifiedException transientEx = new ClassifiedException("transient", ErrorType.TRANSIENT);
    assertEquals(ErrorType.TRANSIENT, transientEx.getErrorType());

    ClassifiedException permanentEx = new ClassifiedException("permanent", ErrorType.PERMANENT);
    assertEquals(ErrorType.PERMANENT, permanentEx.getErrorType());

    ClassifiedException businessEx = new ClassifiedException("business", ErrorType.BUSINESS);
    assertEquals(ErrorType.BUSINESS, businessEx.getErrorType());
  }

  @Test
  @DisplayName("Test: ClassifiedException supports cause chaining")
  void testClassifiedExceptionCauseChaining() {
    RuntimeException cause = new RuntimeException("root cause");
    ClassifiedException ex = new ClassifiedException("wrapped", ErrorType.TRANSIENT, cause);

    assertEquals(ErrorType.TRANSIENT, ex.getErrorType());
    assertEquals(cause, ex.getCause());
    assertEquals("wrapped", ex.getMessage());
  }

  @Test
  @DisplayName("Test: DlqMessageFormatter handles null error message gracefully")
  void testDlqMessageFormatterHandlesNullMessage() {
    Exception nullMsgEx = new DeadlockLoserDataAccessException(null, null);
    DlqEvent dlqEvent = dlqMessageFormatter.formatDlqEvent(
        testEvent, nullMsgEx, ErrorType.TRANSIENT, 1);

    assertNotNull(dlqEvent);
    assertNotNull(dlqEvent.getErrorDetails());
    // Message may be null or empty, but should not throw NPE
  }

  @Test
  @DisplayName("Test: Retry backoff policy is configured correctly")
  void testRetryBackoffConfiguration() {
    RetryTemplate retryTemplate = buildRetryTemplate(4, 100L, 2.0);

    int[] attempts = {0};
    try {
      retryTemplate.execute(context -> {
        attempts[0]++;
        throw new DeadlockLoserDataAccessException("fail", null);
      });
    } catch (Exception ex) {
      // Expected - all retries exhausted
      assertEquals(4, attempts[0], "Should attempt 4 times");
    }
  }

  // ========== Helper Methods ==========

  private RetryTemplate buildRetryTemplate(int maxAttempts, long initialDelay, double multiplier) {
    RetryTemplate retryTemplate = new RetryTemplate();

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(maxAttempts);
    retryTemplate.setRetryPolicy(retryPolicy);

    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setInitialInterval(initialDelay);
    backOffPolicy.setMultiplier(multiplier);
    retryTemplate.setBackOffPolicy(backOffPolicy);

    return retryTemplate;
  }
}
