/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeduplicationService {

  private static final Logger log = LoggerFactory.getLogger(DeduplicationService.class);

  /**
   * Normalize email for deduplication matching. MVP approach: lowercase + trim only.
   *
   * <p>Limitations (documented for interview): - No handling of Gmail dots (john.doe ==
   * johndoe @gmail.com) - No handling of + aliases (john+spam @example.com) - No Unicode
   * normalization - No typo detection (gnail.com vs gmail.com)
   */
  public String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      return null;
    }

    String normalized = email.trim().toLowerCase();

    log.debug("Normalized email: original='{}' -> normalized='{}'", email, normalized);

    return normalized;
  }

  /**
   * Simple name similarity check (MVP version). Could be extended with Levenshtein distance or
   * phonetic matching.
   */
  public boolean namesMatch(String name1, String name2) {
    if (name1 == null && name2 == null) {
      return true;
    }
    if (name1 == null || name2 == null) {
      return false;
    }

    String n1 = name1.trim().toLowerCase();
    String n2 = name2.trim().toLowerCase();

    return n1.equals(n2);
  }
}
