/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Logs all field conflicts to a dedicated conflict-resolution.log file.
 *
 * <p>Each conflict is logged as a structured JSON object for easy analysis.
 */
@Component
public class ConflictLogger {

  private static final Logger conflictLog = LoggerFactory.getLogger("com.mdm.mastering.conflict");

  private final ObjectMapper objectMapper;

  public ConflictLogger() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Logs a field conflict with resolution details.
   *
   * @param nationalId the customer's national ID (masked for privacy)
   * @param conflict the original conflict
   * @param resolution the resolution result
   * @param strategy the strategy used
   */
  public void logConflict(
      String nationalId, FieldConflict conflict, FieldResolution resolution, String strategy) {
    Map<String, Object> logEntry = new LinkedHashMap<>();
    logEntry.put("timestamp", Instant.now());
    logEntry.put("nationalId", maskNationalId(nationalId));
    logEntry.put("fieldName", conflict.fieldName());
    logEntry.put("currentValue", maskIfSensitive(conflict.currentValue()));
    logEntry.put("incomingValue", maskIfSensitive(conflict.incomingValue()));
    logEntry.put("currentSource", conflict.currentSource());
    logEntry.put("incomingSource", conflict.incomingSource());
    logEntry.put("strategy", strategy);
    logEntry.put("resolvedValue", maskIfSensitive(resolution.resolvedValue()));
    logEntry.put("reason", resolution.resolutionReason());

    try {
      String json = objectMapper.writeValueAsString(logEntry);
      conflictLog.info(json);
    } catch (JsonProcessingException e) {
      conflictLog.warn("Failed to serialize conflict log entry: {}", e.getMessage());
      // Fallback to non-JSON logging
      conflictLog.info("Conflict: {}", logEntry);
    }
  }

  private static String maskNationalId(String nationalId) {
    if (nationalId == null || nationalId.length() <= 4) {
      return "***";
    }
    return "***" + nationalId.substring(nationalId.length() - 4);
  }

  private Object maskIfSensitive(Object value) {
    // For MVP, we don't mask field values in conflict logs
    // as they are needed for debugging. In production, consider
    // masking sensitive fields like email, phone, etc.
    return value;
  }
}
