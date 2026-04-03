/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Deduplication Service.
 *
 * <p>Tests cover: - Email normalization - Name matching - Edge cases (null, empty, whitespace)
 */
@ExtendWith(MockitoExtension.class)
class DeduplicationServiceTest {

  private DeduplicationService service;

  @BeforeEach
  void setUp() {
    service = new DeduplicationService();
  }

  @Test
  @DisplayName("Should normalize email to lowercase and trim whitespace")
  void shouldNormalizeEmail() {
    String result = service.normalizeEmail("  John.Doe@EXAMPLE.com ");
    assertEquals("john.doe@example.com", result);
  }

  @Test
  @DisplayName("Should handle email with multiple spaces")
  void shouldHandleMultipleSpaces() {
    String result = service.normalizeEmail("   test@example.com   ");
    assertEquals("test@example.com", result);
  }

  @Test
  @DisplayName("Should return null for null email")
  void shouldReturnNullForNullEmail() {
    String result = service.normalizeEmail(null);
    assertNull(result);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @CsvSource({"   ", "\t", "\n"})
  @DisplayName("Should return null for blank emails")
  void shouldReturnNullForBlankEmails(String email) {
    String result = service.normalizeEmail(email);
    assertNull(result);
  }

  @Test
  @DisplayName("Should preserve email structure after normalization")
  void shouldPreserveEmailStructure() {
    String result = service.normalizeEmail("user.name+tag@subdomain.example.co.uk");
    assertEquals("user.name+tag@subdomain.example.co.uk", result);
  }

  @Test
  @DisplayName("Should match identical names")
  void shouldMatchIdenticalNames() {
    assertTrue(service.namesMatch("John", "John"));
  }

  @Test
  @DisplayName("Should match names case-insensitively")
  void shouldMatchNamesCaseInsensitively() {
    assertTrue(service.namesMatch("john", "JOHN"));
    assertTrue(service.namesMatch("John", "john"));
    assertTrue(service.namesMatch("JOHN", "John"));
  }

  @Test
  @DisplayName("Should match names with different whitespace")
  void shouldMatchNamesWithWhitespace() {
    assertTrue(service.namesMatch("  John  ", "John"));
    assertTrue(service.namesMatch("John", "  John  "));
  }

  @Test
  @DisplayName("Should return false for different names")
  void shouldReturnFalseForDifferentNames() {
    assertFalse(service.namesMatch("John", "Jane"));
    assertFalse(service.namesMatch("John", "Johnny"));
  }

  @Test
  @DisplayName("Should handle null names")
  void shouldHandleNullNames() {
    assertTrue(service.namesMatch(null, null));
    assertFalse(service.namesMatch("John", null));
    assertFalse(service.namesMatch(null, "Jane"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @CsvSource({"   ", "\t"})
  @DisplayName("Should handle blank names")
  void shouldHandleBlankNames(String name) {
    assertFalse(service.namesMatch("John", name));
  }
}
