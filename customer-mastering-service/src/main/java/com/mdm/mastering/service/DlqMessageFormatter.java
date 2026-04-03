/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.dto.DlqEvent;
import com.mdm.mastering.exception.ClassifiedException;
import com.mdm.mastering.exception.ErrorType;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Formats failed events into DLQ event structures with full diagnostic information.
 */
@Service
public class DlqMessageFormatter {

  private static final Logger log = LoggerFactory.getLogger(DlqMessageFormatter.class);

  /**
   * Builds a DLQ event from a failed processing attempt.
   *
   * @param event the original event that failed
   * @param exception the exception that caused failure
   * @param errorType the classified error type
   * @param retryCount number of retry attempts made
   * @return a fully populated DlqEvent ready for DLQ publishing
   */
  public DlqEvent formatDlqEvent(
      CustomerRawEvent event,
      Exception exception,
      ErrorType errorType,
      int retryCount) {

    StringWriter sw = new StringWriter();
    exception.printStackTrace(new PrintWriter(sw));
    String stackTrace = sw.toString();

    String exceptionClassName = exception.getClass().getSimpleName();
    String errorMessage = exception.getMessage();

    DlqEvent.ErrorDetails errorDetails = DlqEvent.ErrorDetails.builder()
        .exception(exceptionClassName)
        .message(truncate(errorMessage, 500))
        .stackTrace(stackTrace)
        .build();

    DlqEvent.ProcessingHistoryEntry historyEntry = DlqEvent.ProcessingHistoryEntry.builder()
        .attempt(retryCount)
        .timestamp(Instant.now())
        .error(truncate(errorMessage, 200))
        .build();

    List<DlqEvent.ProcessingHistoryEntry> history = new ArrayList<>();
    history.add(historyEntry);

    return DlqEvent.builder()
        .originalEvent(event)
        .errorDetails(errorDetails)
        .processingHistory(history)
        .schemaVersion("v1")
        .build();
  }

  /**
   * Classifies an exception into an {@link ErrorType} based on exception class.
   *
   * @param exception the exception to classify
   * @return the classified error type
   */
  public ErrorType classifyException(Exception exception) {
    if (exception instanceof ClassifiedException classified) {
      return classified.getErrorType();
    }

    String className = exception.getClass().getName();

    if (className.contains("ConstraintViolation")
        || className.contains("DataIntegrityViolation")
        || className.contains("IllegalArgumentException")) {
      return ErrorType.PERMANENT;
    }

    if (className.contains("Deadlock")
        || className.contains("QueryTimeout")
        || className.contains("SQLTransient")
        || className.contains("Transient")) {
      return ErrorType.TRANSIENT;
    }

    if (className.contains("Validation")
        || className.contains("Business")) {
      return ErrorType.BUSINESS;
    }

    // Default: treat as transient (retry)
    return ErrorType.TRANSIENT;
  }

  /**
   * Determines if the exception is retryable.
   *
   * @param exception the exception to check
   * @return true if the exception should trigger retry
   */
  public boolean isRetryable(Exception exception) {
    ErrorType errorType = classifyException(exception);
    return errorType == ErrorType.TRANSIENT;
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
  }
}
