/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.controller;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.mdm.ingestion.dto.CustomerIngestionRequest;
import com.mdm.ingestion.dto.CustomerRawEvent;
import com.mdm.ingestion.service.CustomerKafkaProducer;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/customers")
public class CustomerIngestionController {

  private static final Logger log = LoggerFactory.getLogger(CustomerIngestionController.class);

  private final CustomerKafkaProducer kafkaProducer;

  public CustomerIngestionController(CustomerKafkaProducer kafkaProducer) {
    this.kafkaProducer = kafkaProducer;
  }

  /**
   * Ingest customer data asynchronously. Returns 202 Accepted immediately - processing happens
   * async via Kafka.
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('CUSTOMER_WRITE', 'ADMIN')")
  public ResponseEntity<Void> ingestCustomer(
      @Valid @RequestBody CustomerIngestionRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID eventId = UUID.randomUUID();

    String userId = jwt != null ? jwt.getSubject() : "anonymous";
    log.info(
        "Received customer ingestion request: eventId={}, email={}, source={}, userId={}",
        eventId,
        request.getEmail(),
        request.getSourceSystem(),
        userId);

    CustomerRawEvent event =
        CustomerRawEvent.builder()
            .eventId(eventId)
            .email(request.getEmail())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .phone(request.getPhone())
            .sourceSystem(request.getSourceSystem())
            .timestamp(Instant.now())
            .build();

    kafkaProducer
        .send(event)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                log.error(
                    "Failed to publish event to Kafka: eventId={}, error={}",
                    eventId,
                    ex.getMessage());
              } else {
                log.info(
                    "Successfully published event to Kafka: eventId={}, partition={}, offset={}",
                    eventId,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
              }
            });

    return ResponseEntity.accepted().build();
  }
}
