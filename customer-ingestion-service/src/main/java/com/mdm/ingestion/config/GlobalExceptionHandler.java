/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for Customer Ingestion Service.
 *
 * <p>Provides consistent error responses for: - Validation errors (400) - Internal errors (500) -
 * Not found errors (404)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Handle validation errors (400 Bad Request). */
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

  /** Handle illegal argument exceptions (400 Bad Request). */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(ex.getMessage())
            .build();

    log.debug("Illegal argument: {}", ex.getMessage());

    return ResponseEntity.badRequest().body(response);
  }

  /** Handle unexpected internal errors (500 Internal Server Error). */
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

  /** Error response structure. */
  @lombok.Builder
  @lombok.Data
  @lombok.AllArgsConstructor
  @lombok.NoArgsConstructor
  public static class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private Map<String, String> details;
  }
}
