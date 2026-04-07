/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.entity.CustomerGoldenEntity;

/**
 * Logs stale event detections to the dedicated stale-events.log file.
 *
 * <p>Each log entry includes: nationalId (masked), eventVersion, currentVersion, eventTimestamp,
 * and kafkaOffset for audit and debugging purposes.
 *
 * <p>Optionally publishes stale events to the DLQ topic for audit trail.
 */
@Component
public class StaleEventLogger {

  private static final Logger log = LoggerFactory.getLogger("com.mdm.mastering.stale");
  private static final Marker STALE_MARKER = MarkerFactory.getMarker("STALE_EVENT");

  private final DlqProducer dlqProducer;
  private final boolean publishToDlq;

  public StaleEventLogger(
      DlqProducer dlqProducer,
      @Value("${mdm.mastering.stale-events.publish-to-dlq:false}") boolean publishToDlq) {
    this.dlqProducer = dlqProducer;
    this.publishToDlq = publishToDlq;
  }

  /**
   * Logs a detected stale event.
   *
   * @param event the stale event
   * @param existing the current golden record
   * @param kafkaOffset the Kafka offset of the stale event
   */
  public void logStaleEvent(
      CustomerRawEvent event, CustomerGoldenEntity existing, long kafkaOffset) {
    String maskedNationalId = maskNationalId(event.getNationalId());

    log.warn(
        STALE_MARKER,
        "Stale event detected: nationalId={}, eventVersion={}, currentVersion={}, "
            + "eventTimestamp={}, lastProcessedTimestamp={}, kafkaOffset={}, eventId={}",
        maskedNationalId,
        event.getEventVersion(),
        existing.getEventVersion(),
        event.getTimestamp(),
        existing.getLastProcessedEventTimestamp(),
        kafkaOffset,
        event.getEventId());

    // Optionally publish to DLQ for audit
    if (publishToDlq) {
      dlqProducer.sendStaleEventToDlqForAudit(event, existing.getEventVersion(), kafkaOffset);
    }
  }

  private static String maskNationalId(String nationalId) {
    if (nationalId == null || nationalId.length() <= 4) {
      return "***";
    }
    return "***" + nationalId.substring(nationalId.length() - 4);
  }
}
