/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import java.util.List;

import com.mdm.mastering.entity.CustomerGoldenEntity;
import lombok.Getter;

/**
 * Service interface for survivable customer matching.
 *
 * <p><b>DEPRECATED:</b> Not currently used in the main processing flow. Preserved for Phase 2
 * (advanced fuzzy matching).
 */
public interface CustomerMatchingService {

  /**
   * Find matching customer using survivable rules.
   *
   * @param email Customer email
   * @param firstName Customer first name
   * @param lastName Customer last name
   * @param phone Customer phone number
   * @return Match result with score and matched customer (if any)
   */
  MatchResult findMatch(String email, String firstName, String lastName, String phone);

  /** Result of a customer matching attempt. */
  @Getter
  class MatchResult {
    private final MatchType type;
    private final CustomerGoldenEntity matchedCustomer;
    private final double matchScore;
    private final List<String> matchedRules;

    private MatchResult(
        MatchType type,
        CustomerGoldenEntity matchedCustomer,
        double matchScore,
        List<String> matchedRules) {
      this.type = type;
      this.matchedCustomer = matchedCustomer;
      this.matchScore = matchScore;
      this.matchedRules = matchedRules;
    }

    public static MatchResult duplicate(
        CustomerGoldenEntity customer, double score, List<String> rules) {
      return new MatchResult(MatchType.DUPLICATE, customer, score, rules);
    }

    public static MatchResult possibleDuplicate(
        CustomerGoldenEntity customer, double score, List<String> rules) {
      return new MatchResult(MatchType.POSSIBLE_DUPLICATE, customer, score, rules);
    }

    public static MatchResult noMatch() {
      return new MatchResult(MatchType.NO_MATCH, null, 0.0, List.of());
    }

    public boolean isDuplicate() {
      return type == MatchType.DUPLICATE;
    }

    public boolean isPossibleDuplicate() {
      return type == MatchType.POSSIBLE_DUPLICATE;
    }

    public boolean hasMatch() {
      return matchedCustomer != null;
    }

  }

  enum MatchType {
    DUPLICATE,
    POSSIBLE_DUPLICATE,
    NO_MATCH
  }
}
