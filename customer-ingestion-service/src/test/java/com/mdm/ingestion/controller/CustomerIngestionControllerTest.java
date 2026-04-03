/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.controller;

import java.util.UUID;

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
import com.mdm.ingestion.service.IngestionUseCaseService;
import com.mdm.ingestion.service.IngestionUseCaseService.IngestionResponse;
import com.mdm.ingestion.service.IngestionUseCaseService.IngestionStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerIngestionController.class)
class CustomerIngestionControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private IngestionUseCaseService ingestionService;

  @MockBean private CustomerKafkaProducer kafkaProducer;

  private CustomerIngestionRequest validRequest;

  @BeforeEach
  void setUp() {
    validRequest = new CustomerIngestionRequest();
    validRequest.setNationalId("198011225359");
    validRequest.setName("John Doe");
    validRequest.setEmail("john.doe@example.com");
    validRequest.setPhone("+1-555-123-4567");
    validRequest.setSourceSystem("CRM");

    IngestionResponse acceptedResponse =
        new IngestionResponse(UUID.randomUUID(), IngestionStatus.ACCEPTED);
    when(ingestionService.ingest(any(), any())).thenReturn(acceptedResponse);
  }

  @Test
  @DisplayName("Should accept valid customer ingestion request")
  void shouldAcceptValidRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isAccepted())
        .andExpect(header().exists("X-Event-ID"));
  }

  @Test
  @DisplayName("Should accept request with X-Idempotency-Key header")
  void shouldAcceptRequestWithIdempotencyKey() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .header("X-Idempotency-Key", "custom-idempotency-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isAccepted());
  }

  @Test
  @DisplayName("Should reject request with missing nationalId")
  void shouldRejectMissingNationalId() throws Exception {
    validRequest.setNationalId(null);

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
    minimalRequest.setNationalId("198011225359");
    minimalRequest.setSourceSystem("CRM");

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

  @Test
  @DisplayName("Should return 200 OK with cached response on idempotency hit")
  void shouldReturnCachedResponseOnIdempotencyHit() throws Exception {
    UUID eventId = UUID.randomUUID();
    IngestionResponse cachedResponse = new IngestionResponse(eventId, IngestionStatus.CACHED);

    when(ingestionService.ingest(any(), any())).thenReturn(cachedResponse);

    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Event-ID", eventId.toString()))
        .andExpect(header().string("X-Idempotency-Replay", "true"));
  }

  @Test
  @DisplayName("Should return 409 Conflict when request is still processing")
  void shouldReturnConflictWhenProcessing() throws Exception {
    when(ingestionService.ingest(any(), any()))
        .thenThrow(
            new com.mdm.ingestion.service.IngestionUseCaseService.ConcurrentProcessingException(
                "test-key"));

    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Should reject invalid nationalId format (too short)")
  void shouldRejectShortNationalId() throws Exception {
    validRequest.setNationalId("12345");

    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should reject invalid source system")
  void shouldRejectInvalidSourceSystem() throws Exception {
    validRequest.setSourceSystem("INVALID");

    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isBadRequest());
  }
}
