/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.entity.CustomerGoldenEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for out-of-order event handling using StaleEventChecker.
 *
 * <p>Test scenarios:
 *
 * <ol>
 *   <li>In-order events: v1 → v2 → v3 → all applied
 *   <li>Out-of-order events: v1 → v3 → v2 → v2 detected as stale
 *   <li>Same version, different timestamps: later timestamp wins
 *   <li>Null version handling: events without versions are not considered stale
 * </ol>
 */
class OutOfOrderEventTest {

  private StaleEventChecker staleEventChecker;

  @BeforeEach
  void setUp() {
    staleEventChecker = new StaleEventChecker();
  }

  // ========== Test 1: In-order events ==========

  @Test
  @DisplayName("Test 1: In-order events — none should be stale")
  void testInOrderEvents_noneShouldBeStale() {
    // Simulate golden record at version 0 (does not exist yet)
    // Event v1 arrives — no existing record, so not stale
    CustomerRawEvent eventV1 = buildEvent(1L, Instant.parse("2026-01-01T10:00:00Z"));

    // After v1 is applied, golden record is at version 1
    CustomerGoldenEntity goldenV1 =
        buildGoldenRecord("123456", 1L, Instant.parse("2026-01-01T10:00:00Z"));

    // Event v2 arrives — version 2 > 1, not stale
    CustomerRawEvent eventV2 = buildEvent(2L, Instant.parse("2026-01-01T10:01:00Z"));
    assertFalse(staleEventChecker.isStale(eventV2, goldenV1), "Event v2 should NOT be stale");

    // After v2 is applied, golden record is at version 2
    CustomerGoldenEntity goldenV2 =
        buildGoldenRecord("123456", 2L, Instant.parse("2026-01-01T10:01:00Z"));

    // Event v3 arrives — version 3 > 2, not stale
    CustomerRawEvent eventV3 = buildEvent(3L, Instant.parse("2026-01-01T10:02:00Z"));
    assertFalse(staleEventChecker.isStale(eventV3, goldenV2), "Event v3 should NOT be stale");
  }

  // ========== Test 2: Out-of-order events ==========

  @Test
  @DisplayName("Test 2: Out-of-order events — v1 → v3 → v2, v2 should be stale")
  void testOutOfOrderEvents_v2ShouldBeStale() {
    // Event v1 applied, golden at version 1
    CustomerGoldenEntity goldenV1 =
        buildGoldenRecord("789012", 1L, Instant.parse("2026-01-01T10:00:00Z"));

    // Event v3 arrives before v2 — version 3 > 1, not stale
    CustomerRawEvent eventV3 = buildEvent(3L, Instant.parse("2026-01-01T10:02:00Z"));
    assertFalse(staleEventChecker.isStale(eventV3, goldenV1), "Event v3 should NOT be stale");

    // After v3 is applied, golden at version 3
    CustomerGoldenEntity goldenV3 =
        buildGoldenRecord("789012", 3L, Instant.parse("2026-01-01T10:02:00Z"));

    // Event v2 arrives — version 2 < 3, STALE
    CustomerRawEvent eventV2 = buildEvent(2L, Instant.parse("2026-01-01T10:01:00Z"));
    assertTrue(
        staleEventChecker.isStale(eventV2, goldenV3), "Event v2 should be stale (version 2 < 3)");
  }

  // ========== Test 3: Same version, different timestamps ==========

  @Test
  @DisplayName("Test 3: Same version, older timestamp should be stale")
  void testSameVersionDifferentTimestamp_olderShouldBeStale() {
    // Golden record at version 1, processed at 10:00:05
    CustomerGoldenEntity golden =
        buildGoldenRecord("345678", 1L, Instant.parse("2026-01-01T10:00:05Z"));

    // Event v1 @ 10:00:00 arrives — same version but older timestamp → stale
    CustomerRawEvent olderEvent = buildEvent(1L, Instant.parse("2026-01-01T10:00:00Z"));
    assertTrue(
        staleEventChecker.isStale(olderEvent, golden),
        "Older event with same version should be stale");

    // Event v1 @ 10:00:10 arrives — same version but newer timestamp → NOT stale
    CustomerRawEvent newerEvent = buildEvent(1L, Instant.parse("2026-01-01T10:00:10Z"));
    assertFalse(
        staleEventChecker.isStale(newerEvent, golden),
        "Newer event with same version should NOT be stale");
  }

  @Test
  @DisplayName("Test 3b: Same version, same timestamp — not stale (tie)")
  void testSameVersionSameTimestamp_notStale() {
    CustomerGoldenEntity golden =
        buildGoldenRecord("345678", 1L, Instant.parse("2026-01-01T10:00:00Z"));

    CustomerRawEvent event = buildEvent(1L, Instant.parse("2026-01-01T10:00:00Z"));
    assertFalse(
        staleEventChecker.isStale(event, golden),
        "Same version and timestamp should NOT be stale (tie)");
  }

  // ========== Test 4: Null version handling ==========

  @Test
  @DisplayName("Test 4: Events without version should not be considered stale")
  void testNullVersion_notConsideredStale() {
    CustomerGoldenEntity golden =
        buildGoldenRecord("901234", 5L, Instant.parse("2026-01-01T10:00:00Z"));

    // Event without version
    CustomerRawEvent eventWithoutVersion = buildEvent(null, Instant.parse("2026-01-01T10:01:00Z"));
    assertFalse(
        staleEventChecker.isStale(eventWithoutVersion, golden),
        "Event without version should NOT be considered stale (cannot determine)");

    // Golden record without eventVersion
    CustomerGoldenEntity goldenWithoutVersion = buildGoldenRecord("901234", null, null);
    CustomerRawEvent event = buildEvent(3L, Instant.parse("2026-01-01T10:01:00Z"));
    assertFalse(
        staleEventChecker.isStale(event, goldenWithoutVersion),
        "Event against golden without version should NOT be stale");
  }

  // ========== Additional edge cases ==========

  @Test
  @DisplayName("Test 5: Event version significantly higher than current — not stale")
  void testEventVersionMuchHigher_notStale() {
    CustomerGoldenEntity golden =
        buildGoldenRecord("567890", 2L, Instant.parse("2026-01-01T10:00:00Z"));

    CustomerRawEvent event = buildEvent(100L, Instant.parse("2026-01-01T12:00:00Z"));
    assertFalse(
        staleEventChecker.isStale(event, golden),
        "Event with much higher version should NOT be stale");
  }

  @Test
  @DisplayName("Test 6: Null timestamp in event — version-based check still works")
  void testNullTimestamp_versionCheckStillWorks() {
    CustomerGoldenEntity golden =
        buildGoldenRecord("111222", 5L, Instant.parse("2026-01-01T10:00:00Z"));

    // Event with lower version but null timestamp
    CustomerRawEvent event = buildEvent(3L, null);
    assertTrue(
        staleEventChecker.isStale(event, golden),
        "Event with lower version should be stale even with null timestamp");
  }

  // ========== Helper methods ==========

  private CustomerRawEvent buildEvent(Long eventVersion, Instant timestamp) {
    return CustomerRawEvent.builder()
        .eventId(UUID.randomUUID())
        .eventVersion(eventVersion)
        .nationalId("1234567890")
        .name("Test User")
        .email("test@example.com")
        .phone("+1234567890")
        .sourceSystem("TEST")
        .timestamp(timestamp)
        .build();
  }

  private CustomerGoldenEntity buildGoldenRecord(
      String nationalId, Long eventVersion, Instant lastProcessedTimestamp) {
    return CustomerGoldenEntity.builder()
        .id(UUID.randomUUID())
        .nationalId(nationalId)
        .name("Existing User")
        .email("existing@example.com")
        .phone("+0987654321")
        .version(1L)
        .eventVersion(eventVersion)
        .lastProcessedEventTimestamp(lastProcessedTimestamp)
        .createdAt(Instant.parse("2026-01-01T09:00:00Z"))
        .updatedAt(Instant.parse("2026-01-01T09:00:00Z"))
        .lastSourceSystem("TEST")
        .build();
  }
}
