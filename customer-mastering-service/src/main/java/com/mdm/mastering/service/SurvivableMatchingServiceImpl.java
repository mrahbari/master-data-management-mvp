/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mdm.mastering.entity.CustomerGoldenEntity;
import com.mdm.mastering.repository.CustomerGoldenRepository;

/**
 * Survivable Matching Service Implementation - Advanced Duplicate Detection.
 *
 * <p><b>DEPRECATED:</b> This service is not currently used in the main processing flow. The
 * GoldenRecordService now uses nationalId as the canonical unique identifier for deduplication.
 * This service is preserved for future use when advanced fuzzy matching is required (Phase 2 of the
 * roadmap).
 *
 * <p>Implements multiple fuzzy matching rules to detect duplicates even when data varies:
 *
 * <p>Rule 1: Email Exact Match (normalized) Rule 2: Name Similarity (Levenshtein distance) Rule 3:
 * Phonetic Matching (Soundex/Metaphone) Rule 4: Nickname Mapping (John=Jon=Johnny) Rule 5: Phone
 * Number Normalization Rule 6: Combined Scoring (weighted match)
 *
 * <p>Matching Strategy: - Each rule contributes to a total match score - Score >= 80% → Considered
 * duplicate - Score 50-79% → Possible duplicate (flag for review) - Score < 50% → Not a duplicate
 */
@Service
public class SurvivableMatchingServiceImpl implements CustomerMatchingService {

  private static final Logger log = LoggerFactory.getLogger(SurvivableMatchingServiceImpl.class);

  private final CustomerGoldenRepository goldenRepository;

  // Matching thresholds
  private static final double DUPLICATE_THRESHOLD = 0.80;
  private static final double POSSIBLE_THRESHOLD = 0.50;
  private static final int LEVENSHTEIN_THRESHOLD = 2;

  // Rule weights
  private static final double WEIGHT_EMAIL = 0.40;
  private static final double WEIGHT_NAME = 0.25;
  private static final double WEIGHT_PHONE = 0.20;
  private static final double WEIGHT_PHONETIC = 0.15;

  public SurvivableMatchingServiceImpl(CustomerGoldenRepository goldenRepository) {
    this.goldenRepository = goldenRepository;
  }

  @Override
  public MatchResult findMatch(String email, String firstName, String lastName, String phone) {
    // Use nationalId-based matching as primary, fall back to fuzzy matching
    String normalizedEmail = normalizeEmail(email);

    // Use repository-level query instead of loading entire table into memory
    Optional<CustomerGoldenEntity> exactMatch = Optional.empty();
    if (normalizedEmail != null) {
      exactMatch = goldenRepository.findByEmailIgnoreCase(normalizedEmail);
    }

    if (exactMatch.isPresent()) {
      return MatchResult.duplicate(exactMatch.get(), 1.0, List.of("EMAIL_EXACT_MATCH"));
    }

    // For now, return no match. Fuzzy matching logic preserved for future use.
    return MatchResult.noMatch();
  }

  private String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      return null;
    }
    return email.trim().toLowerCase();
  }

  private String normalizeName(String name) {
    if (name == null) return "";
    return name.trim().toLowerCase();
  }

  private String normalizePhone(String phone) {
    if (phone == null) return "";
    String digits = phone.replaceAll("\\D", "");
    if (digits.startsWith("1") && digits.length() == 11) {
      digits = digits.substring(1);
    }
    return digits;
  }

  private int levenshteinDistance(String s1, String s2) {
    int[][] dp = new int[s1.length() + 1][s2.length() + 1];
    for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
    for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;
    for (int i = 1; i <= s1.length(); i++) {
      for (int j = 1; j <= s2.length(); j++) {
        int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
        dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
      }
    }
    return dp[s1.length()][s2.length()];
  }

  private String soundex(String name) {
    if (name == null || name.isEmpty()) return "";
    name = name.toUpperCase();
    char firstChar = name.charAt(0);
    StringBuilder encoded = new StringBuilder();
    encoded.append(firstChar);
    char lastCode = getCodeForChar(firstChar);
    for (int i = 1; i < name.length() && encoded.length() < 4; i++) {
      char code = getCodeForChar(name.charAt(i));
      if (code != lastCode && code != '0') encoded.append(code);
      lastCode = code;
    }
    while (encoded.length() < 4) encoded.append('0');
    return encoded.toString();
  }

  private char getCodeForChar(char c) {
    return switch (c) {
      case 'B', 'F', 'P', 'V' -> '1';
      case 'C', 'G', 'J', 'K', 'Q', 'S', 'X', 'Z' -> '2';
      case 'D', 'T' -> '3';
      case 'L' -> '4';
      case 'M', 'N' -> '5';
      case 'R' -> '6';
      default -> '0';
    };
  }

  private double scoreEmailMatch(String email1, String email2) {
    if (email1 == null || email2 == null) return 0.0;
    if (email1.equals(email2)) return 1.0;
    int distance = levenshteinDistance(email1.toLowerCase(), email2.toLowerCase());
    double similarity = 1.0 - ((double) distance / Math.max(email1.length(), email2.length()));
    return Math.max(0.0, similarity);
  }

  private double scoreNameSimilarity(String name1, String name2) {
    if (name1 == null || name2 == null) return 0.0;
    String n1 = normalizeName(name1);
    String n2 = normalizeName(name2);
    if (n1.equals(n2)) return 1.0;
    if (n1.isEmpty() || n2.isEmpty()) return 0.0;
    int distance = levenshteinDistance(n1, n2);
    if (distance <= LEVENSHTEIN_THRESHOLD) {
      return 1.0 - ((double) distance / Math.max(n1.length(), n2.length()));
    }
    return n1.contains(n2) || n2.contains(n1) ? 0.7 : 0.0;
  }

  private double scorePhoneticMatch(
      String firstName1, String firstName2, String lastName1, String lastName2) {
    double firstScore = 0.0, lastScore = 0.0;
    if (firstName1 != null
        && firstName2 != null
        && !firstName1.isEmpty()
        && !firstName2.isEmpty()) {
      firstScore =
          soundex(normalizeName(firstName1)).equals(soundex(normalizeName(firstName2))) ? 1.0 : 0.0;
    }
    if (lastName1 != null && lastName2 != null && !lastName1.isEmpty() && !lastName2.isEmpty()) {
      lastScore =
          soundex(normalizeName(lastName1)).equals(soundex(normalizeName(lastName2))) ? 1.0 : 0.0;
    }
    return (firstScore > 0 || lastScore > 0) ? (firstScore + lastScore) / 2.0 : 0.0;
  }

  private double scoreNicknameMatch(String name1, String name2) {
    if (name1 == null || name2 == null) return 0.0;
    String n1 = normalizeName(name1);
    String n2 = normalizeName(name2);
    if (n1.equals(n2)) return 1.0;
    String canonical1 = getCanonicalName(n1);
    String canonical2 = getCanonicalName(n2);
    return canonical1.equals(canonical2) ? 0.9 : 0.0;
  }

  private String getCanonicalName(String name) {
    return NICKNAME_MAP.getOrDefault(name.toLowerCase(), name.toLowerCase());
  }

  private double scorePhoneMatch(String phone1, String phone2) {
    if (phone1 == null || phone2 == null) return 0.0;
    String p1 = normalizePhone(phone1);
    String p2 = normalizePhone(phone2);
    if (p1.isEmpty() || p2.isEmpty()) return 0.0;
    if (p1.equals(p2)) return 1.0;
    if (p1.length() >= 7 && p2.length() >= 7) {
      String last7_1 = p1.substring(p1.length() - 7);
      String last7_2 = p2.substring(p2.length() - 7);
      if (last7_1.equals(last7_2)) return 0.7;
    }
    return 0.0;
  }

  private double scoreEmailMatchFuzzy(String email1, String email2) {
    if (email1 == null || email2 == null) return 0.0;
    String e1 = email1.toLowerCase();
    String e2 = email2.toLowerCase();
    if (e1.contains("@gmail.com") && e2.contains("@gmail.com")) {
      String e1NoDots = e1.replace(".", "");
      String e2NoDots = e2.replace(".", "");
      if (e1NoDots.equals(e2NoDots)) return 0.95;
    }
    return scoreEmailMatch(email1, email2);
  }

  private static final Map<String, String> NICKNAME_MAP =
          Map.<String, String>ofEntries(
              Map.entry("john", "john"),
              Map.entry("jon", "john"),
              Map.entry("jonny", "john"),
              Map.entry("johnny", "john"),
              Map.entry("jack", "john"),
              Map.entry("jock", "john"),
              Map.entry("ian", "john"),
              Map.entry("robert", "robert"),
              Map.entry("rob", "robert"),
              Map.entry("bob", "robert"),
              Map.entry("bobby", "robert"),
              Map.entry("robbie", "robert"),
              Map.entry("william", "william"),
              Map.entry("will", "william"),
              Map.entry("bill", "william"),
              Map.entry("billy", "william"),
              Map.entry("willy", "william"),
              Map.entry("richard", "richard"),
              Map.entry("rich", "richard"),
              Map.entry("rick", "richard"),
              Map.entry("ricky", "richard"),
              Map.entry("dick", "richard"),
              Map.entry("michael", "michael"),
              Map.entry("mike", "michael"),
              Map.entry("mikey", "michael"),
              Map.entry("mick", "michael"),
              Map.entry("joseph", "joseph"),
              Map.entry("joe", "joseph"),
              Map.entry("joey", "joseph"),
              Map.entry("christopher", "christopher"),
              Map.entry("chris", "christopher"),
              Map.entry("topher", "christopher"),
              Map.entry("daniel", "daniel"),
              Map.entry("dan", "daniel"),
              Map.entry("danny", "daniel"),
              Map.entry("matthew", "matthew"),
              Map.entry("matt", "matthew"),
              Map.entry("matty", "matthew"),
              Map.entry("alexander", "alexander"),
              Map.entry("alex", "alexander"),
              Map.entry("al", "alexander"),
              Map.entry("nicholas", "nicholas"),
              Map.entry("nick", "nicholas"),
              Map.entry("nicky", "nicholas"),
              Map.entry("anthony", "anthony"),
              Map.entry("tony", "anthony"),
              Map.entry("ant", "anthony"),
              Map.entry("elizabeth", "elizabeth"),
              Map.entry("liz", "elizabeth"),
              Map.entry("beth", "elizabeth"),
              Map.entry("katherine", "katherine"),
              Map.entry("kate", "katherine"),
              Map.entry("kathy", "katherine"),
              Map.entry("catherine", "katherine"),
              Map.entry("jennifer", "jennifer"),
              Map.entry("jen", "jennifer"),
              Map.entry("jenny", "jennifer"),
              Map.entry("patricia", "patricia"),
              Map.entry("pat", "patricia"),
              Map.entry("barbara", "barbara"),
              Map.entry("barb", "barbara"),
              Map.entry("margaret", "margaret"),
              Map.entry("maggie", "margaret"),
              Map.entry("dorothy", "dorothy"),
              Map.entry("dot", "dorothy"));
}
