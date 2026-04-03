/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.exception;

/** Thrown when a request is still being processed concurrently (HTTP 409). */
public final class ConcurrentProcessingException extends IngestionDomainException {

  private final String keyHash;

  public ConcurrentProcessingException(String keyHash) {
    super("Request with idempotency key hash [%s] is still being processed".formatted(keyHash));
    this.keyHash = keyHash;
  }

  public String getKeyHash() {
    return keyHash;
  }
}
