/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mdm.ingestion.dto.CustomerIngestionRequest;
import com.mdm.ingestion.service.IngestionUseCaseService;
import com.mdm.ingestion.service.IngestionUseCaseService.IngestionResponse;
import com.mdm.ingestion.service.IngestionUseCaseService.IngestionStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/customers")
public class CustomerIngestionController {

  private final IngestionUseCaseService ingestionService;

  @PostMapping
  @PreAuthorize("hasAnyRole('CUSTOMER_WRITE', 'ADMIN')")
  public ResponseEntity<Void> ingestCustomer(
      @Valid @RequestBody CustomerIngestionRequest request,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt) {

    String userId = jwt != null ? jwt.getSubject() : "anonymous";
    log.info(
        "Received customer ingestion request: nationalId={}, source={}, userId={}",
        request.getNationalId(),
        request.getSourceSystem(),
        userId);

    IngestionResponse response = ingestionService.ingest(request, idempotencyKey);

    if (response.status() == IngestionStatus.CACHED) {
      return ResponseEntity.ok()
          .header("X-Event-ID", response.eventId().toString())
          .header("X-Idempotency-Replay", "true")
          .build();
    }

    return ResponseEntity.accepted().header("X-Event-ID", response.eventId().toString()).build();
  }
}
