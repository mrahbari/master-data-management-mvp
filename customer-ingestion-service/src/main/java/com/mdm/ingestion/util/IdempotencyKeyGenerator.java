/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class IdempotencyKeyGenerator {

  private static final String ALGORITHM = "SHA-256";

  private IdempotencyKeyGenerator() {}

  public static String generate(String nationalId, String sourceSystem) {
    String input =
        InputSanitizer.normalizeNationalId(nationalId).toLowerCase()
            + "|"
            + InputSanitizer.sanitize(sourceSystem).toUpperCase();

    try {
      MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
      byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
