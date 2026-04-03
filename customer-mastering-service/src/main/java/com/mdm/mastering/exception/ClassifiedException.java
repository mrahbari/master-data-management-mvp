/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.exception;

/**
 * Base exception for classified errors in the mastering service.
 * Carries an {@link ErrorType} to drive retry/DLQ routing decisions.
 */
public class ClassifiedException extends RuntimeException {

  private final ErrorType errorType;

  public ClassifiedException(String message, ErrorType errorType) {
    super(message);
    this.errorType = errorType;
  }

  public ClassifiedException(String message, ErrorType errorType, Throwable cause) {
    super(message, cause);
    this.errorType = errorType;
  }

  public ErrorType getErrorType() {
    return errorType;
  }
}
