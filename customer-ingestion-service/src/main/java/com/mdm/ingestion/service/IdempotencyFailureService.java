/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.service;

import java.util.UUID;

import com.mdm.ingestion.util.SensitiveDataMasker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mdm.ingestion.entity.IdempotencyKey.IdempotencyStatus;
import com.mdm.ingestion.repository.IdempotencyKeyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles idempotency key failure updates in a separate transaction.
 *
 * <p>This service ensures that failKey updates are committed even when the calling transaction
 * rolls back, preventing permanent lockouts on idempotency keys.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class IdempotencyFailureService {

  private final IdempotencyKeyRepository repository;

  /**
   * Marks an idempotency key as failed in a new transaction.
   *
   * <p>Uses REQUIRES_NEW propagation to ensure the failure status is persisted even if the caller's
   * transaction rolls back.
   *
   * @param keyHash the SHA-256 hash of the deterministic key
   * @param eventId the event ID that failed
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failKey(String keyHash, UUID eventId) {
    int updated = repository.completeByKeyHash(keyHash, IdempotencyStatus.FAILED);
    if (updated > 0) {
      log.warn(
          "Idempotency key marked failed (isolated tx): keyHash={}, eventId={}",
          maskKey(keyHash),
          eventId);
    } else {
      log.debug(
          "Idempotency key was not in PROCESSING state, skip fail: keyHash={}", maskKey(keyHash));
    }
  }

  private static String maskKey(String key) {
      return SensitiveDataMasker.maskHash(key);
  }
}
