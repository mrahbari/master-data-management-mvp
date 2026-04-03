/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.util;

import java.text.Normalizer;

public final class InputSanitizer {

  private InputSanitizer() {}

  public static String sanitize(String input) {
    if (input == null) {
      return null;
    }
    return Normalizer.normalize(input.trim(), Normalizer.Form.NFC);
  }

  public static String normalizeNationalId(String nationalId) {
    if (nationalId == null) {
      return null;
    }
    return nationalId.trim().replaceAll("[^a-zA-Z0-9]", "");
  }

  public static String normalizeEmail(String email) {
    if (email == null) {
      return null;
    }
    return email.trim().toLowerCase();
  }
}
