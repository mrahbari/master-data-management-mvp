/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.util;

/** Utility for masking sensitive data before logging or exposing in responses. */
public final class SensitiveDataMasker {

  private SensitiveDataMasker() {
    // Utility class
  }

  /** Masks a national ID, showing only the last 4 characters. */
  public static String maskNationalId(String nationalId) {
    if (nationalId == null || nationalId.length() <= 4) {
      return "***";
    }
    return "***" + nationalId.substring(nationalId.length() - 4);
  }
}
