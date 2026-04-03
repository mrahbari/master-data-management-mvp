/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.validator;

import java.util.Set;

import com.mdm.ingestion.util.InputSanitizer;

public final class CustomerRequestValidator {

  private static final Set<String> ALLOWED_SOURCE_SYSTEMS =
      Set.of("CRM", "BANK", "WEB", "MOBILE", "PARTNER", "GOVERNMENT");

  private static final int MIN_NATIONAL_ID_LENGTH = 12;
  private static final int MAX_NATIONAL_ID_LENGTH = 13;

  private CustomerRequestValidator() {}

  public static void validateNationalId(String nationalId) {
    if (nationalId == null || nationalId.isBlank()) {
      throw new ValidationException("nationalId is required");
    }

    String normalized = InputSanitizer.normalizeNationalId(nationalId);
    if (normalized.length() < MIN_NATIONAL_ID_LENGTH
        || normalized.length() > MAX_NATIONAL_ID_LENGTH) {
      throw new ValidationException(
          "nationalId must be between "
              + MIN_NATIONAL_ID_LENGTH
              + " and "
              + MAX_NATIONAL_ID_LENGTH
              + " alphanumeric characters");
    }

    if (!normalized.matches("^[a-zA-Z0-9]+$")) {
      throw new ValidationException("nationalId must contain only alphanumeric characters");
    }
  }

  public static void validateSourceSystem(String sourceSystem) {
    if (sourceSystem == null || sourceSystem.isBlank()) {
      throw new ValidationException("sourceSystem is required");
    }

    String sanitized = InputSanitizer.sanitize(sourceSystem).toUpperCase();
    if (!ALLOWED_SOURCE_SYSTEMS.contains(sanitized)) {
      throw new ValidationException(
          "sourceSystem must be one of: " + String.join(", ", ALLOWED_SOURCE_SYSTEMS));
    }
  }

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }
}
