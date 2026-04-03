/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.exception;

/** Thrown when a Kafka publish operation fails (HTTP 500). */
public final class KafkaPublishException extends IngestionDomainException {

  private final String eventId;
  private final String keyHash;

  public KafkaPublishException(String eventId, String keyHash, Throwable cause) {
    super(
        "Failed to publish event eventId=%s, keyHash=%s".formatted(eventId, maskKey(keyHash)),
        cause);
    this.eventId = eventId;
    this.keyHash = keyHash;
  }

  public String getEventId() {
    return eventId;
  }

  public String getKeyHash() {
    return keyHash;
  }

  private static String maskKey(String key) {
    if (key == null || key.length() <= 8) {
      return "***";
    }
    return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
  }
}
