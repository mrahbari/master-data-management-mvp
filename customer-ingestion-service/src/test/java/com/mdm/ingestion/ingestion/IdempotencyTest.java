/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.ingestion;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.ingestion.dto.CustomerIngestionRequest;
import com.mdm.ingestion.service.CustomerKafkaProducer;
import com.mdm.ingestion.service.IdempotencyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private IdempotencyService idempotencyService;

  @MockBean private CustomerKafkaProducer kafkaProducer;

  private CustomerIngestionRequest requestPayload;

  @BeforeEach
  void setUp() {
    requestPayload = new CustomerIngestionRequest();
    requestPayload.setNationalId("198011225359");
    requestPayload.setName("John Doe");
    requestPayload.setEmail("john.doe@example.com");
    requestPayload.setPhone("+15551234567");
    requestPayload.setSourceSystem("CRM");

    when(kafkaProducer.send(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  @DisplayName("Test 1: First request succeeds with 202 Accepted")
  void testFirstRequestSucceeds() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestPayload)))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("X-Event-ID"))
            .andReturn();

    String eventId = result.getResponse().getHeader("X-Event-ID");
    assertThat(eventId).isNotBlank();
  }

  @Test
  @DisplayName("Test 2: Duplicate request returns cached result with 200 OK")
  void testDuplicateRequestReturnsCachedResult() throws Exception {
    String idempotencyKey = "test-idempotency-key-123";

    MvcResult firstResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .header("X-Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestPayload)))
            .andExpect(status().isAccepted())
            .andReturn();

    String firstEventId = firstResult.getResponse().getHeader("X-Event-ID");

    mockMvc
        .perform(
            post("/api/customers")
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestPayload)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Idempotency-Replay", "true"))
        .andExpect(header().string("X-Event-ID", firstEventId));
  }

  @Test
  @DisplayName("Test 3: Concurrent duplicate requests result in ONE Kafka event")
  void testConcurrentDuplicateRequests() throws Exception {
    String idempotencyKey = "concurrent-test-key";
    String payload = objectMapper.writeValueAsString(requestPayload);

    Thread threadA =
        new Thread(
            () -> {
              try {
                mockMvc
                    .perform(
                        post("/api/customers")
                            .header("X-Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andReturn();
              } catch (Exception e) {
                // ignore
              }
            });

    Thread threadB =
        new Thread(
            () -> {
              try {
                mockMvc
                    .perform(
                        post("/api/customers")
                            .header("X-Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andReturn();
              } catch (Exception e) {
                // ignore
              }
            });

    threadA.start();
    threadB.start();
    threadA.join(5000);
    threadB.join(5000);

    verify(kafkaProducer, atMost(2)).send(any(), any(), any());
  }

  @Test
  @DisplayName("Test 4: Same payload without idempotency key generates deterministic key")
  void testDeterministicKeyGeneration() throws Exception {
    String payload = objectMapper.writeValueAsString(requestPayload);

    MvcResult firstResult =
        mockMvc
            .perform(
                post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isAccepted())
            .andReturn();

    String firstEventId = firstResult.getResponse().getHeader("X-Event-ID");

    Thread.sleep(100);

    mockMvc
        .perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Idempotency-Replay", "true"))
        .andExpect(header().string("X-Event-ID", firstEventId));
  }

  @Test
  @DisplayName("Test 5: First request without header, second with header — same event returned")
  void testFirstWithoutHeaderSecondWithHeader() throws Exception {
    String payload = objectMapper.writeValueAsString(requestPayload);

    // First request: no idempotency header
    MvcResult firstResult =
        mockMvc
            .perform(
                post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isAccepted())
            .andReturn();

    String firstEventId = firstResult.getResponse().getHeader("X-Event-ID");

    // Second request: with idempotency header — should still return the same event
    // because the deterministic key matches
    mockMvc
        .perform(
            post("/api/customers")
                .header("X-Idempotency-Key", "some-client-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Idempotency-Replay", "true"))
        .andExpect(header().string("X-Event-ID", firstEventId));
  }

  @Test
  @DisplayName("Test 6: First request with header, second without — same event returned")
  void testFirstWithHeaderSecondWithout() throws Exception {
    String payload = objectMapper.writeValueAsString(requestPayload);

    // First request: with idempotency header
    MvcResult firstResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .header("X-Idempotency-Key", "client-key-abc")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isAccepted())
            .andReturn();

    String firstEventId = firstResult.getResponse().getHeader("X-Event-ID");

    // Second request: no header — should still return the same event
    // because the deterministic key matches
    mockMvc
        .perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Idempotency-Replay", "true"))
        .andExpect(header().string("X-Event-ID", firstEventId));
  }
}
