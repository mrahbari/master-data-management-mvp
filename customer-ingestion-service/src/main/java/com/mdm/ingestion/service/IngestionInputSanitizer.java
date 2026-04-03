/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.service;

import com.mdm.ingestion.dto.CustomerIngestionRequest;
import com.mdm.ingestion.util.InputSanitizer;
import com.mdm.ingestion.validator.CustomerRequestValidator;
import org.springframework.stereotype.Component;

/**
 * Sanitizes and validates ingestion request inputs.
 *
 * <p>This component encapsulates the single responsibility of transforming raw input
 * into clean, validated domain values. It follows the Single Responsibility Principle
 * by separating input normalization from business orchestration.
 */
@Component
public final class IngestionInputSanitizer {

  /**
   * Sanitizes and validates all fields in the request.
   *
   * @param request the raw ingestion request
   * @return a sanitized request with normalized values
   * @throws com.mdm.ingestion.exception.IngestionDomainException if validation fails
   */
  public SanitizedRequest sanitize(CustomerIngestionRequest request) {
    String nationalId = sanitizeNationalId(request.getNationalId());
    String sourceSystem = sanitizeSourceSystem(request.getSourceSystem());
    String name = sanitizeNullable(request.getName());
    String email = sanitizeNullable(request.getEmail());
    String phone = sanitizeNullable(request.getPhone());

    CustomerRequestValidator.validateNationalId(nationalId);
    CustomerRequestValidator.validateSourceSystem(sourceSystem);

    return new SanitizedRequest(nationalId, name, email, phone, sourceSystem);
  }

  private String sanitizeNationalId(String nationalId) {
      return InputSanitizer.normalizeNationalId(nationalId);
  }

  private String sanitizeSourceSystem(String sourceSystem) {
    if (sourceSystem == null) {
      return null;
    }
    return sourceSystem.trim().toUpperCase();
  }

  private String sanitizeNullable(String value) {
    if (value == null) {
      return null;
    }
    return java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFC);
  }

  /**
   * Immutable value object representing a fully sanitized and validated ingestion request.
   */
  public record SanitizedRequest(
      String nationalId,
      String name,
      String email,
      String phone,
      String sourceSystem
  ) {
    public SanitizedRequest {
      if (nationalId == null || nationalId.isBlank()) {
        throw new IllegalArgumentException("nationalId is required");
      }
      if (sourceSystem == null || sourceSystem.isBlank()) {
        throw new IllegalArgumentException("sourceSystem is required");
      }
    }
  }
}
