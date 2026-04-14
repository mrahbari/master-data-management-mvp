/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mdm.ingestion.exception.ConcurrentProcessingException;
import com.mdm.ingestion.exception.KafkaPublishException;
import com.mdm.ingestion.validator.CustomerRequestValidator.ValidationException;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationExceptions(
      MethodArgumentNotValidException ex) {

    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message("Validation failed")
            .details(errors)
            .build();

    log.debug("Validation error: {}", errors);

    return ResponseEntity.badRequest().body(response);
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(ex.getMessage())
            .build();

    log.debug("Validation error: {}", ex.getMessage());

    return ResponseEntity.badRequest().body(response);
  }

  @ExceptionHandler(ConcurrentProcessingException.class)
  public ResponseEntity<Void> handleConcurrentProcessing(ConcurrentProcessingException ex) {
    log.info("Concurrent request detected: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).build();
  }

  @ExceptionHandler(KafkaPublishException.class)
  public ResponseEntity<ErrorResponse> handlePublishFailure(KafkaPublishException ex) {
    log.error("Failed to publish event to Kafka: {}", ex.getMessage(), ex);

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("Failed to process request")
            .build();

    return ResponseEntity.internalServerError().body(response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleInternalError(Exception ex) {
    log.error("Internal error", ex);

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred")
            .build();

    return ResponseEntity.internalServerError().body(response);
  }

  @Builder
  public record ErrorResponse(
      Instant timestamp, int status, String error, String message, Map<String, String> details) {}
}
