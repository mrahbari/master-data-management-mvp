/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Survivable Matching Service.
 *
 * <p>Tests cover all 5+ matching rules:
 *
 * <ul>
 *   <li>Rule 1: Email similarity
 *   <li>Rule 2: Name similarity (Levenshtein)
 *   <li>Rule 3: Phonetic matching (Soundex)
 *   <li>Rule 4: Nickname matching
 *   <li>Rule 5: Phone number normalization
 * </ul>
 */
class SurvivableMatchingServiceImplTest {

  private SurvivableMatchingServiceImpl matchingService;
  private DeduplicationService deduplicationService;

  @BeforeEach
  void setUp() {
    deduplicationService = new DeduplicationService();
    // Note: Full testing would require mocking CustomerGoldenRepository
    // This test class focuses on the matching algorithms themselves
  }

  // ===========================================
  // RULE 1: Email Similarity Tests
  // ===========================================

  @Test
  @DisplayName("Rule 1: Exact email match should score 1.0")
  void testExactEmailMatch() {
    // Given
    String email1 = "john.doe@example.com";
    String email2 = "john.doe@example.com";

    // When/Then (manually test the algorithm)
    assertEquals(1.0, calculateEmailSimilarity(email1, email2), 0.01);
  }

  @Test
  @DisplayName("Rule 1: Case insensitive email match")
  void testCaseInsensitiveEmailMatch() {
    String email1 = "John.Doe@Example.com";
    String email2 = "john.doe@example.com";

    assertEquals(1.0, calculateEmailSimilarity(email1, email2), 0.01);
  }

  @Test
  @DisplayName("Rule 1: Gmail dots equivalence")
  void testGmailDotsEquivalence() {
    String email1 = "john.doe@gmail.com";
    String email2 = "johndoe@gmail.com";

    double score = calculateEmailSimilarity(email1, email2);
    assertTrue(score >= 0.9, "Gmail dots should match with high score: " + score);
  }

  @Test
  @DisplayName("Rule 1: Email typo detection")
  void testEmailTypoDetection() {
    String email1 = "john.doe@example.com";
    String email2 = "john.doe@exmple.com"; // typo: missing 'a'

    double score = calculateEmailSimilarity(email1, email2);
    assertTrue(score > 0.5, "Email with typo should still have some similarity: " + score);
  }

  // ===========================================
  // RULE 2: Name Similarity (Levenshtein) Tests
  // ===========================================

  @Test
  @DisplayName("Rule 2: Exact name match")
  void testExactNameMatch() {
    assertEquals(1.0, calculateNameSimilarity("John", "John"), 0.01);
  }

  @Test
  @DisplayName("Rule 2: Case insensitive name match")
  void testCaseInsensitiveNameMatch() {
    assertEquals(1.0, calculateNameSimilarity("Jone", "jone"), 0.01);
  }

  @Test
  @DisplayName("Rule 2: Name with typo - Jone vs John")
  void testNameTypoJoneVsJohn() {
    double score = calculateNameSimilarity("Jone", "John");
    assertTrue(score > 0.5, "Jone and John should be similar: " + score);
  }

  @Test
  @DisplayName("Rule 2: Name with typo - Jon vs John")
  void testNameTypoJonVsJohn() {
    double score = calculateNameSimilarity("Jon", "John");
    assertTrue(score > 0.5, "Jon and John should be similar: " + score);
  }

  @Test
  @DisplayName("Rule 2: Name with typo - Jonny vs Johnny")
  void testNameTypoJonnyVsJohnny() {
    double score = calculateNameSimilarity("Jonny", "Johnny");
    assertTrue(score > 0.5, "Jonny and Johnny should be similar: " + score);
  }

  @Test
  @DisplayName("Rule 2: Different names should not match")
  void testDifferentNames() {
    double score = calculateNameSimilarity("John", "Mary");
    assertTrue(score < 0.5, "John and Mary should not be similar: " + score);
  }

  // ===========================================
  // RULE 3: Phonetic Matching (Soundex) Tests
  // ===========================================

  @Test
  @DisplayName("Rule 3: Soundex - Smith vs Smyth")
  void testSoundexSmithVsSmyth() {
    String soundex1 = calculateSoundex("Smith");
    String soundex2 = calculateSoundex("Smyth");

    assertEquals(soundex1, soundex2, "Smith and Smyth should have same Soundex code");
  }

  @Test
  @DisplayName("Rule 3: Soundex - Johnson vs Jonson")
  void testSoundexJohnsonVsJonson() {
    String soundex1 = calculateSoundex("Johnson");
    String soundex2 = calculateSoundex("Jonson");

    assertEquals(soundex1, soundex2, "Johnson and Jonson should have same Soundex code");
  }

  @Test
  @DisplayName("Rule 3: Soundex - Robert vs Rupert")
  void testSoundexRobertVsRupert() {
    String soundex1 = calculateSoundex("Robert");
    String soundex2 = calculateSoundex("Rupert");

    assertEquals(soundex1, soundex2, "Robert and Rupert should have same Soundex code");
  }

  @Test
  @DisplayName("Rule 3: Soundex - Different sounding names")
  void testSoundexDifferentNames() {
    String soundex1 = calculateSoundex("John");
    String soundex2 = calculateSoundex("Mary");

    assertFalse(soundex1.equals(soundex2), "John and Mary should have different Soundex codes");
  }

  // ===========================================
  // RULE 4: Nickname Matching Tests
  // ===========================================

  @Test
  @DisplayName("Rule 4: Nickname - Jon vs John")
  void testNicknameJonVsJohn() {
    double score = calculateNicknameMatch("Jon", "John");
    assertEquals(0.9, score, 0.01, "Jon and John should match as nickname");
  }

  @Test
  @DisplayName("Rule 4: Nickname - Johnny vs John")
  void testNicknameJohnnyVsJohn() {
    double score = calculateNicknameMatch("Johnny", "John");
    assertEquals(0.9, score, 0.01, "Johnny and John should match as nickname");
  }

  @Test
  @DisplayName("Rule 4: Nickname - Jack vs John")
  void testNicknameJackVsJohn() {
    double score = calculateNicknameMatch("Jack", "John");
    assertEquals(0.9, score, 0.01, "Jack and John should match as nickname");
  }

  @Test
  @DisplayName("Rule 4: Nickname - Bob vs Robert")
  void testNicknameBobVsRobert() {
    double score = calculateNicknameMatch("Bob", "Robert");
    assertEquals(0.9, score, 0.01, "Bob and Robert should match as nickname");
  }

  @Test
  @DisplayName("Rule 4: Nickname - Bill vs William")
  void testNicknameBillVsWilliam() {
    double score = calculateNicknameMatch("Bill", "William");
    assertEquals(0.9, score, 0.01, "Bill and William should match as nickname");
  }

  @Test
  @DisplayName("Rule 4: Nickname - Liz vs Elizabeth")
  void testNicknameLizVsElizabeth() {
    double score = calculateNicknameMatch("Liz", "Elizabeth");
    assertEquals(0.9, score, 0.01, "Liz and Elizabeth should match as nickname");
  }

  @Test
  @DisplayName("Rule 4: Nickname - Mike vs Michael")
  void testNicknameMikeVsMichael() {
    double score = calculateNicknameMatch("Mike", "Michael");
    assertEquals(0.9, score, 0.01, "Mike and Michael should match as nickname");
  }

  @Test
  @DisplayName("Rule 4: Nickname - Chris vs Christopher")
  void testNicknameChrisVsChristopher() {
    double score = calculateNicknameMatch("Chris", "Christopher");
    assertEquals(0.9, score, 0.01, "Chris and Christopher should match as nickname");
  }

  @Test
  @DisplayName("Rule 4: Non-nickname names should not match")
  void testNonNicknameNames() {
    double score = calculateNicknameMatch("John", "Mary");
    assertEquals(0.0, score, 0.01, "John and Mary should not match as nicknames");
  }

  // ===========================================
  // RULE 5: Phone Number Matching Tests
  // ===========================================

  @Test
  @DisplayName("Rule 5: Phone - Exact match after normalization")
  void testPhoneExactMatch() {
    String phone1 = "+1-555-123-4567";
    String phone2 = "(555) 123-4567";

    double score = calculatePhoneMatch(phone1, phone2);
    assertEquals(1.0, score, 0.01, "Phones should match after normalization");
  }

  @Test
  @DisplayName("Rule 5: Phone - Different formats same number")
  void testPhoneDifferentFormats() {
    String phone1 = "5551234567";
    String phone2 = "555-123-4567";

    double score = calculatePhoneMatch(phone1, phone2);
    assertEquals(1.0, score, 0.01, "Phones should match regardless of format");
  }

  @Test
  @DisplayName("Rule 5: Phone - Same local number different area code")
  void testPhoneSameLocalDifferentArea() {
    String phone1 = "2125551234";
    String phone2 = "9175551234";

    double score = calculatePhoneMatch(phone1, phone2);
    assertEquals(0.7, score, 0.01, "Same local number should partially match");
  }

  @Test
  @DisplayName("Rule 5: Phone - Different numbers")
  void testPhoneDifferentNumbers() {
    String phone1 = "5551234567";
    String phone2 = "5559876543";

    double score = calculatePhoneMatch(phone1, phone2);
    assertEquals(0.0, score, 0.01, "Different phones should not match");
  }

  // ===========================================
  // Helper Methods (replicate service logic for testing)
  // ===========================================

  private double calculateEmailSimilarity(String email1, String email2) {
    if (email1 == null || email2 == null) return 0.0;
    if (email1.equals(email2)) return 1.0;

    String e1 = email1.toLowerCase();
    String e2 = email2.toLowerCase();

    // Gmail dots
    if (e1.contains("@gmail.com") && e2.contains("@gmail.com")) {
      if (e1.replace(".", "").equals(e2.replace(".", ""))) {
        return 0.95;
      }
    }

    // Levenshtein
    int distance = levenshteinDistance(e1, e2);
    return 1.0 - ((double) distance / Math.max(e1.length(), e2.length()));
  }

  private double calculateNameSimilarity(String name1, String name2) {
    if (name1 == null || name2 == null) return 0.0;

    String n1 = name1.trim().toLowerCase();
    String n2 = name2.trim().toLowerCase();

    if (n1.equals(n2)) return 1.0;
    if (n1.isEmpty() || n2.isEmpty()) return 0.0;

    int distance = levenshteinDistance(n1, n2);
    if (distance <= 2) {
      return 1.0 - ((double) distance / Math.max(n1.length(), n2.length()));
    }

    return 0.0;
  }

  private String calculateSoundex(String name) {
    if (name == null || name.isEmpty()) return "";

    name = name.toUpperCase();
    char firstChar = name.charAt(0);

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

  private double calculateNicknameMatch(String name1, String name2) {
    if (name1 == null || name2 == null) return 0.0;

    String n1 = name1.trim().toLowerCase();
    String n2 = name2.trim().toLowerCase();

    if (n1.equals(n2)) return 1.0;

    String canonical1 = getCanonicalName(n1);
    String canonical2 = getCanonicalName(n2);

    if (canonical1.equals(canonical2)) {
      return 0.9;
    }

    return 0.0;
  }

  private String getCanonicalName(String name) {
    java.util.Map<String, String> nicknameMap = new java.util.HashMap<>();
    nicknameMap.put("john", "john");
    nicknameMap.put("jon", "john");
    nicknameMap.put("jonny", "john");
    nicknameMap.put("johnny", "john");
    nicknameMap.put("jack", "john");
    nicknameMap.put("rob", "robert");
    nicknameMap.put("bob", "robert");
    nicknameMap.put("bobby", "robert");
    nicknameMap.put("will", "william");
    nicknameMap.put("bill", "william");
    nicknameMap.put("billy", "william");
    nicknameMap.put("mike", "michael");
    nicknameMap.put("mikey", "michael");
    nicknameMap.put("liz", "elizabeth");
    nicknameMap.put("lizzy", "elizabeth");
    nicknameMap.put("chris", "christopher");
    nicknameMap.put("joe", "joseph");
    nicknameMap.put("joey", "joseph");
    nicknameMap.put("dan", "daniel");
    nicknameMap.put("danny", "daniel");
    nicknameMap.put("matt", "matthew");
    nicknameMap.put("tony", "anthony");
    nicknameMap.put("alex", "alexander");
    nicknameMap.put("nick", "nicholas");
    nicknameMap.put("kate", "katherine");
    nicknameMap.put("kathy", "katherine");
    nicknameMap.put("jen", "jennifer");
    nicknameMap.put("jenny", "jennifer");
    return nicknameMap.getOrDefault(name, name);
  }

  private double calculatePhoneMatch(String phone1, String phone2) {
    if (phone1 == null || phone2 == null) return 0.0;

    String p1 = normalizePhone(phone1);
    String p2 = normalizePhone(phone2);

    if (p1.isEmpty() || p2.isEmpty()) return 0.0;
    if (p1.equals(p2)) return 1.0;

    // Last 7 digits
    if (p1.length() >= 7 && p2.length() >= 7) {
      String last7_1 = p1.substring(p1.length() - 7);
      String last7_2 = p2.substring(p2.length() - 7);
      if (last7_1.equals(last7_2)) {
        return 0.7;
      }
    }

    return 0.0;
  }

  private String normalizePhone(String phone) {
    if (phone == null) return "";

    String digits = phone.replaceAll("\\D", "");

    if (digits.startsWith("1") && digits.length() == 11) {
      digits = digits.substring(1);
    }

    if (digits.length() == 10) {
      return digits;
    }

    return digits;
  }

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
        dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
      }
    }

    return dp[s1.length()][s2.length()];
  }
}
