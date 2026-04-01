/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.controller;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.ingestion.dto.CustomerIngestionRequest;
import com.mdm.ingestion.service.CustomerKafkaProducer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for Customer Ingestion Controller.
 *
 * <p>Tests cover: - Valid customer ingestion - Validation errors (missing fields, invalid email) -
 * Error handling
 */
@WebMvcTest(CustomerIngestionController.class)
class CustomerIngestionControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private CustomerKafkaProducer kafkaProducer;

  private CustomerIngestionRequest validRequest;

  @BeforeEach
  void setUp() {
    validRequest = new CustomerIngestionRequest();
    validRequest.setEmail("john.doe@example.com");
    validRequest.setFirstName("John");
    validRequest.setLastName("Doe");
    validRequest.setPhone("+1-555-123-4567");
    validRequest.setSourceSystem("web-portal");

    // Mock successful Kafka publish
    when(kafkaProducer.send(any())).thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  @DisplayName("Should accept valid customer ingestion request")
  void shouldAcceptValidRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isAccepted());
  }

  @Test
  @DisplayName("Should reject request with missing email")
  void shouldRejectMissingEmail() throws Exception {
    validRequest.setEmail(null);

    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should reject request with invalid email format")
  void shouldRejectInvalidEmail() throws Exception {
    validRequest.setEmail("invalid-email");

    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should reject request with missing source system")
  void shouldRejectMissingSourceSystem() throws Exception {
    validRequest.setSourceSystem(null);

    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should accept request with only required fields")
  void shouldAcceptMinimalRequest() throws Exception {
    CustomerIngestionRequest minimalRequest = new CustomerIngestionRequest();
    minimalRequest.setEmail("test@example.com");
    minimalRequest.setSourceSystem("test");

    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(minimalRequest)))
        .andExpect(status().isAccepted());
  }

  @Test
  @DisplayName("Should reject request with empty body")
  void shouldRejectEmptyBody() throws Exception {
    mockMvc
        .perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should reject request without content type")
  void shouldRejectWithoutContentType() throws Exception {
    mockMvc
        .perform(post("/api/customers").content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isUnsupportedMediaType());
  }
}
