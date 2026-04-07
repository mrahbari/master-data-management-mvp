/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import org.springframework.stereotype.Component;

import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.entity.CustomerGoldenEntity;

/**
 * Determines whether an incoming event is stale relative to the current golden record state.
 *
 * <p>Uses a hybrid logical clock approach:
 *
 * <ol>
 *   <li>Primary ordering: eventVersion field
 *   <li>Secondary ordering: eventTimestamp
 * </ol>
 *
 * <p>An event is considered stale if:
 *
 * <ul>
 *   <li>Its version is lower than the golden record's current eventVersion
 *   <li>Its version equals the current eventVersion but its timestamp is older than the last
 *       processed event timestamp
 * </ul>
 */
@Component
public class StaleEventChecker {

  /**
   * Checks if the given event is stale compared to the existing golden record.
   *
   * @param event the incoming event
   * @param existing the current golden record
   * @return true if the event is stale and should not be applied
   */
  public boolean isStale(CustomerRawEvent event, CustomerGoldenEntity existing) {
    Long eventVersion = event.getEventVersion();
    Long currentEventVersion = existing.getEventVersion();

    // If event has no version, we cannot determine staleness — treat as not stale
    if (eventVersion == null || currentEventVersion == null) {
      return false;
    }

    // If event version is lower than current, it's stale
    if (eventVersion < currentEventVersion) {
      return true;
    }

    // If versions are equal but event timestamp is older than last processed, it's stale
    if (eventVersion.equals(currentEventVersion)) {
      java.time.Instant eventTimestamp = event.getTimestamp();
      java.time.Instant lastProcessed = existing.getLastProcessedEventTimestamp();

      if (eventTimestamp != null
          && lastProcessed != null
          && eventTimestamp.isBefore(lastProcessed)) {
        return true;
      }
    }

    return false;
  }
}
