/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.util;

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

  /** Masks an email address, showing only the first character and domain. */
  public static String maskEmail(String email) {
    if (email == null || email.isBlank()) {
      return "***";
    }
    int atIndex = email.indexOf('@');
    if (atIndex <= 1) {
      return "***@" + (atIndex >= 0 ? email.substring(atIndex + 1) : "???");
    }
    return email.charAt(0) + "***@" + email.substring(atIndex + 1);
  }

  /** Masks a hash key, showing only the first 4 and last 4 characters. */
  public static String maskHash(String key) {
    if (key == null || key.length() <= 8) {
      return "***";
    }
    return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
  }
}
