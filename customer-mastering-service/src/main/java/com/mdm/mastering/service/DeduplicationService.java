/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Deduplication utility service.
 *
 * <p><b>DEPRECATED:</b> Not currently used in the main processing flow. The GoldenRecordService now
 * handles deduplication directly using nationalId. Preserved for future reuse when advanced
 * normalization is needed.
 */
@Service
public class DeduplicationService {

  private static final Logger log = LoggerFactory.getLogger(DeduplicationService.class);

  /** Normalize email for deduplication matching. */
  public String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      return null;
    }
    String normalized = email.trim().toLowerCase();
    log.debug("Normalized email: original='{}' -> normalized='{}'", email, normalized);
    return normalized;
  }

  /** Normalize national ID for deduplication matching. */
  public String normalizeNationalId(String nationalId) {
    if (nationalId == null || nationalId.isBlank()) {
      return null;
    }
    return nationalId.trim().replaceAll("[^a-zA-Z0-9]", "");
  }

  /** Simple name similarity check (MVP version). */
  public boolean namesMatch(String name1, String name2) {
    if (name1 == null && name2 == null) return true;
    if (name1 == null || name2 == null) return false;
    return name1.trim().equalsIgnoreCase(name2.trim());
  }
}
