/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.exception;

/** Base exception for domain-specific errors in the ingestion service. */
public abstract class IngestionDomainException extends RuntimeException {

  protected IngestionDomainException(String message) {
    super(message);
  }

  protected IngestionDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
