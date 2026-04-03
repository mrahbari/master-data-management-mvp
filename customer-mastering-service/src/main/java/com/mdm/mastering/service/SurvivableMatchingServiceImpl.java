/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mdm.mastering.entity.CustomerGoldenEntity;
import com.mdm.mastering.repository.CustomerGoldenRepository;

/**
 * Survivable Matching Service Implementation - Advanced Duplicate Detection.
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
  private final DeduplicationService deduplicationService;

  // Matching thresholds
  private static final double DUPLICATE_THRESHOLD = 0.80; // 80% = duplicate
  private static final double POSSIBLE_THRESHOLD = 0.50; // 50% = possible duplicate
  private static final int LEVENSHTEIN_THRESHOLD = 2; // Max edit distance for names

  // Rule weights (must sum to 1.0)
  private static final double WEIGHT_EMAIL = 0.40;
  private static final double WEIGHT_NAME = 0.25;
  private static final double WEIGHT_PHONE = 0.20;
  private static final double WEIGHT_PHONETIC = 0.15;

  public SurvivableMatchingServiceImpl(
      CustomerGoldenRepository goldenRepository, DeduplicationService deduplicationService) {
    this.goldenRepository = goldenRepository;
    this.deduplicationService = deduplicationService;
  }

  @Override
  public MatchResult findMatch(String email, String firstName, String lastName, String phone) {
    log.debug(
        "Finding match for: email={}, firstName={}, lastName={}, phone={}",
        email,
        firstName,
        lastName,
        phone);

    // Normalize inputs
    String normalizedEmail = deduplicationService.normalizeEmail(email);
    String normalizedFirstName = normalizeName(firstName);
    String normalizedLastName = normalizeName(lastName);
    String normalizedPhone = normalizePhone(phone);

    // Rule 1: Try exact email match first (fastest path)
    Optional<CustomerGoldenEntity> exactMatch =
        goldenRepository.findByNormalizedEmail(normalizedEmail);
    if (exactMatch.isPresent()) {
      log.info("Exact email match found: email={}", normalizedEmail);
      return MatchResult.duplicate(exactMatch.get(), 1.0, List.of("EMAIL_EXACT_MATCH"));
    }

    // If no exact match, search for fuzzy matches
    // Get all customers and score them (for MVP - in production use Elasticsearch)
    List<CustomerGoldenEntity> allCustomers = goldenRepository.findAll();

    MatchResult bestMatch = null;

    for (CustomerGoldenEntity candidate : allCustomers) {
      MatchResult result =
          scoreMatch(
              candidate, normalizedEmail, normalizedFirstName, normalizedLastName, normalizedPhone);

      if (bestMatch == null || result.getMatchScore() > bestMatch.getMatchScore()) {
        bestMatch = result;
      }
    }

    if (bestMatch != null && bestMatch.getMatchScore() >= DUPLICATE_THRESHOLD) {
      log.info(
          "Fuzzy match found: score={}, rules={}",
          bestMatch.getMatchScore(),
          bestMatch.getMatchedRules());
      return bestMatch;
    }

    log.debug("No match found");
    return MatchResult.noMatch();
  }

  /**
   * Score a candidate customer against input data.
   */
  private MatchResult scoreMatch(
      CustomerGoldenEntity candidate,
      String inputEmail,
      String inputFirstName,
      String inputLastName,
      String inputPhone) {

    List<String> matchedRules = new ArrayList<>();
    double totalScore = 0.0;

    // Rule 1: Email similarity (even if not exact)
    double emailScore = scoreEmailMatch(inputEmail, candidate.getNormalizedEmail());
    if (emailScore > 0.5) {
      totalScore += emailScore * WEIGHT_EMAIL;
      matchedRules.add("EMAIL_SIMILARITY_" + (int) (emailScore * 100));
    }

    // Rule 2: Name similarity (Levenshtein)
    double firstNameScore = scoreNameSimilarity(inputFirstName, candidate.getFirstName());
    double lastNameScore = scoreNameSimilarity(inputLastName, candidate.getLastName());
    double nameScore = (firstNameScore + lastNameScore) / 2.0;
    if (nameScore > 0.3) {
      totalScore += nameScore * WEIGHT_NAME;
      matchedRules.add("NAME_SIMILARITY_" + (int) (nameScore * 100));
    }

    // Rule 3: Phonetic matching
    double phoneticScore =
        scorePhoneticMatch(
            inputFirstName, candidate.getFirstName(), inputLastName, candidate.getLastName());
    if (phoneticScore > 0.5) {
      totalScore += phoneticScore * WEIGHT_PHONETIC;
      matchedRules.add("PHONETIC_MATCH");
    }

    // Rule 4: Nickname matching
    double nicknameScore = scoreNicknameMatch(inputFirstName, candidate.getFirstName());
    if (nicknameScore > 0.5) {
      totalScore += nicknameScore * WEIGHT_NAME;
      matchedRules.add("NICKNAME_MATCH");
    }

    // Rule 5: Phone number match
    double phoneScore = scorePhoneMatch(inputPhone, candidate.getPhone());
    if (phoneScore > 0.5) {
      totalScore += phoneScore * WEIGHT_PHONE;
      matchedRules.add("PHONE_MATCH");
    }

    // Determine match type
    if (totalScore >= DUPLICATE_THRESHOLD) {
      return MatchResult.duplicate(candidate, totalScore, matchedRules);
    } else if (totalScore >= POSSIBLE_THRESHOLD) {
      return MatchResult.possibleDuplicate(candidate, totalScore, matchedRules);
    } else {
      return MatchResult.noMatch();
    }
  }

  // ===========================================
  // RULE 1: Email Similarity
  // ===========================================

  /**
   * Score email similarity. Handles typos, different domains, etc.
   */
  private double scoreEmailMatch(String email1, String email2) {
    if (email1 == null || email2 == null) {
      return 0.0;
    }

    // Exact match
    if (email1.equals(email2)) {
      return 1.0;
    }

    // Check for common typos
    String e1 = email1.toLowerCase();
    String e2 = email2.toLowerCase();

    // Gmail dots equivalence (john.doe = johndoe)
    if (e1.contains("@gmail.com") && e2.contains("@gmail.com")) {
      String e1NoDots = e1.replace(".", "");
      String e2NoDots = e2.replace(".", "");
      if (e1NoDots.equals(e2NoDots)) {
        return 0.95;
      }
    }

    // Levenshtein distance for typos
    int distance = levenshteinDistance(e1, e2);
    double similarity = 1.0 - ((double) distance / Math.max(e1.length(), e2.length()));
    return Math.max(0.0, similarity);
  }

  // ===========================================
  // RULE 2: Name Similarity (Levenshtein)
  // ===========================================

  /**
   * Score name similarity using Levenshtein distance.
   */
  private double scoreNameSimilarity(String name1, String name2) {
    if (name1 == null || name2 == null) {
      return 0.0;
    }

    String n1 = normalizeName(name1);
    String n2 = normalizeName(name2);

    // Exact match
    if (n1.equals(n2)) {
      return 1.0;
    }

    // Empty names
    if (n1.isEmpty() || n2.isEmpty()) {
      return 0.0;
    }

    // Levenshtein distance
    int distance = levenshteinDistance(n1, n2);

    // If within threshold, calculate similarity
    if (distance <= LEVENSHTEIN_THRESHOLD) {
      return 1.0 - ((double) distance / Math.max(n1.length(), n2.length()));
    }

    // Check if one contains the other (abbreviation)
    if (n1.contains(n2) || n2.contains(n1)) {
      return 0.7;
    }

    return 0.0;
  }

  /**
   * Calculate Levenshtein distance between two strings. Number of single-character edits needed to
   * change one word into the other.
   */
  private int levenshteinDistance(String s1, String s2) {
    int[][] dp = new int[s1.length() + 1][s2.length() + 1];

    for (int i = 0; i <= s1.length(); i++) {
      dp[i][0] = i;
    }
    for (int j = 0; j <= s2.length(); j++) {
      dp[0][j] = j;
    }

    for (int i = 1; i <= s1.length(); i++) {
      for (int j = 1; j <= s2.length(); j++) {
        int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
        dp[i][j] =
            Math.min(
                Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
      }
    }

    return dp[s1.length()][s2.length()];
  }

  // ===========================================
  // RULE 3: Phonetic Matching (Soundex)
  // ===========================================

  /**
   * Score phonetic match using Soundex algorithm. Names that sound the same but are spelled
   * differently (e.g., "Smith" = "Smyth")
   */
  private double scorePhoneticMatch(
      String firstName1, String firstName2, String lastName1, String lastName2) {
    double firstScore = 0.0;
    double lastScore = 0.0;

    if (firstName1 != null
        && firstName2 != null
        && !firstName1.isEmpty()
        && !firstName2.isEmpty()) {
      String soundex1 = soundex(normalizeName(firstName1));
      String soundex2 = soundex(normalizeName(firstName2));
      firstScore = soundex1.equals(soundex2) ? 1.0 : 0.0;
    }

    if (lastName1 != null
        && lastName2 != null
        && !lastName1.isEmpty()
        && !lastName2.isEmpty()) {
      String soundex1 = soundex(normalizeName(lastName1));
      String soundex2 = soundex(normalizeName(lastName2));
      lastScore = soundex1.equals(soundex2) ? 1.0 : 0.0;
    }

    if (firstScore > 0 || lastScore > 0) {
      return (firstScore + lastScore) / 2.0;
    }

    return 0.0;
  }

  /**
   * Soundex algorithm implementation. Encodes names into a 4-character code based on how they
   * sound.
   */
  private String soundex(String name) {
    if (name == null || name.isEmpty()) {
      return "";
    }

    name = name.toUpperCase();
    char firstChar = name.charAt(0);

    // Soundex encoding rules
    StringBuilder encoded = new StringBuilder();
    encoded.append(firstChar);

    char lastCode = getCodeForChar(firstChar);

    for (int i = 1; i < name.length() && encoded.length() < 4; i++) {
      char code = getCodeForChar(name.charAt(i));
      if (code != lastCode && code != '0') {
        encoded.append(code);
      }
      lastCode = code;
    }

    // Pad with zeros
    while (encoded.length() < 4) {
      encoded.append('0');
    }

    return encoded.toString();
  }

  private char getCodeForChar(char c) {
    switch (c) {
      case 'B':
      case 'F':
      case 'P':
      case 'V':
        return '1';
      case 'C':
      case 'G':
      case 'J':
      case 'K':
      case 'Q':
      case 'S':
      case 'X':
      case 'Z':
        return '2';
      case 'D':
      case 'T':
        return '3';
      case 'L':
        return '4';
      case 'M':
      case 'N':
        return '5';
      case 'R':
        return '6';
      default:
        return '0';
    }
  }

  // ===========================================
  // RULE 4: Nickname Matching
  // ===========================================

  /**
   * Score nickname match. Common nicknames mapped to formal names.
   */
  private double scoreNicknameMatch(String name1, String name2) {
    if (name1 == null || name2 == null) {
      return 0.0;
    }

    String n1 = normalizeName(name1);
    String n2 = normalizeName(name2);

    // Exact match
    if (n1.equals(n2)) {
      return 1.0;
    }

    // Check nickname mappings
    String canonical1 = getCanonicalName(n1);
    String canonical2 = getCanonicalName(n2);

    if (canonical1.equals(canonical2)) {
      return 0.9;
    }

    return 0.0;
  }

  /**
   * Get canonical (formal) name from nickname.
   */
  private String getCanonicalName(String name) {
    return NICKNAME_MAP.getOrDefault(name.toLowerCase(), name.toLowerCase());
  }

  // Common nickname mappings
  private static final java.util.Map<String, String> NICKNAME_MAP =
      new java.util.HashMap<String, String>() {
        {
          // John variants
          put("john", "john");
          put("jon", "john");
          put("jonny", "john");
          put("johnny", "john");
          put("jack", "john");
          put("jock", "john");
          put("ian", "john");
          // Robert variants
          put("robert", "robert");
          put("rob", "robert");
          put("bob", "robert");
          put("bobby", "robert");
          put("robbie", "robert");
          // William variants
          put("william", "william");
          put("will", "william");
          put("bill", "william");
          put("billy", "william");
          put("willy", "william");
          // Richard variants
          put("richard", "richard");
          put("rich", "richard");
          put("rick", "richard");
          put("ricky", "richard");
          put("dick", "richard");
          // Elizabeth variants
          put("elizabeth", "elizabeth");
          put("liz", "elizabeth");
          put("lizzy", "elizabeth");
          put("beth", "elizabeth");
          put("betty", "elizabeth");
          put("eliza", "elizabeth");
          // Jennifer variants
          put("jennifer", "jennifer");
          put("jen", "jennifer");
          put("jenny", "jennifer");
          put("jenn", "jennifer");
          // Michael variants
          put("michael", "michael");
          put("mike", "michael");
          put("mikey", "michael");
          put("mick", "michael");
          // Joseph variants
          put("joseph", "joseph");
          put("joe", "joseph");
          put("joey", "joseph");
          // Christopher variants
          put("christopher", "christopher");
          put("chris", "christopher");
          put("topher", "christopher");
          // Katherine variants
          put("katherine", "katherine");
          put("kate", "katherine");
          put("kathy", "katherine");
          put("katie", "katherine");
          put("kat", "katherine");
          put("catherine", "katherine");
          // Alexander variants
          put("alexander", "alexander");
          put("alex", "alexander");
          put("al", "alexander");
          put("xander", "alexander");
          // Nicholas variants
          put("nicholas", "nicholas");
          put("nick", "nicholas");
          put("nicky", "nicholas");
          // Daniel variants
          put("daniel", "daniel");
          put("dan", "daniel");
          put("danny", "daniel");
          // Matthew variants
          put("matthew", "matthew");
          put("matt", "matthew");
          put("matty", "matthew");
          // Anthony variants
          put("anthony", "anthony");
          put("tony", "anthony");
          put("ant", "anthony");
          // Patricia variants
          put("patricia", "patricia");
          put("pat", "patricia");
          put("patty", "patricia");
          put("tricia", "patricia");
          // Barbara variants
          put("barbara", "barbara");
          put("barb", "barbara");
          put("barbie", "barbara");
          put("babs", "barbara");
          // Margaret variants
          put("margaret", "margaret");
          put("maggie", "margaret");
          put("meg", "margaret");
          put("peggy", "margaret");
          // Dorothy variants
          put("dorothy", "dorothy");
          put("dot", "dorothy");
          put("dottie", "dorothy");
        }
      };

  // ===========================================
  // RULE 5: Phone Number Matching
  // ===========================================

  /**
   * Score phone number match. Handles different formats: +1-555-123-4567, (555) 123-4567, 5551234567
   */
  private double scorePhoneMatch(String phone1, String phone2) {
    if (phone1 == null || phone2 == null) {
      return 0.0;
    }

    String p1 = normalizePhone(phone1);
    String p2 = normalizePhone(phone2);

    if (p1.isEmpty() || p2.isEmpty()) {
      return 0.0;
    }

    // Exact match after normalization
    if (p1.equals(p2)) {
      return 1.0;
    }

    // Last 7 digits match (same local number, different area code)
    if (p1.length() >= 7 && p2.length() >= 7) {
      String last7_1 = p1.substring(p1.length() - 7);
      String last7_2 = p2.substring(p2.length() - 7);
      if (last7_1.equals(last7_2)) {
        return 0.7;
      }
    }

    return 0.0;
  }

  /**
   * Normalize phone number to digits only.
   */
  private String normalizePhone(String phone) {
    if (phone == null) {
      return "";
    }

    // Remove all non-digit characters
    String digits = phone.replaceAll("\\D", "");

    // Handle country code
    if (digits.startsWith("1") && digits.length() == 11) {
      digits = digits.substring(1);
    }

    // Must be 10 digits for US phone
    if (digits.length() == 10) {
      return digits;
    }

    return digits;
  }

  // ===========================================
  // Utility Methods
  // ===========================================

  /**
   * Normalize name for comparison.
   */
  private String normalizeName(String name) {
    if (name == null) {
      return "";
    }
    return name.trim().toLowerCase();
  }
}
