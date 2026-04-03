/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.mastering.dto.CustomerRawEvent;
import com.mdm.mastering.dto.DlqEvent;
import com.mdm.mastering.exception.ErrorType;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Producer service responsible for publishing failed events to the Dead Letter Queue.
 *
 * <p>Enriches DLQ messages with Kafka headers containing metadata for downstream
 * tooling and reprocessing capabilities.
 */
@Service
public class DlqProducer {

  private static final Logger log = LoggerFactory.getLogger(DlqProducer.class);
  private static final String HEADER_ORIGINAL_TOPIC = "X-Original-Topic";
  private static final String HEADER_OFFSET = "X-Original-Offset";
  private static final String HEADER_ERROR_TYPE = "X-Error-Type";
  private static final String HEADER_ERROR_MESSAGE = "X-Error-Message";
  private static final String HEADER_RETRY_COUNT = "X-Retry-Count";
  private static final String HEADER_TIMESTAMP = "X-Original-Timestamp";
  private static final String HEADER_SCHEMA_VERSION = "X-Schema-Version";
  private static final String SCHEMA_VERSION = "v1";

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final String dlqTopic;

  public DlqProducer(
      KafkaTemplate<String, String> kafkaTemplate,
      ObjectMapper objectMapper,
      @Value("${kafka.topics.customer-dlq:customer.dlq}") String dlqTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.dlqTopic = dlqTopic;
  }

  /**
   * Sends a failed event to the Dead Letter Queue with full diagnostic headers.
   *
   * @param event the original event that failed processing
   * @param dlqEvent the DLQ wrapper containing error details and processing history
   * @param errorType classification of the error
   * @param retryCount number of retry attempts made
   * @param originalTopic the Kafka topic the event came from
   * @param offset the original Kafka offset
   */
  public void sendToDlq(
      CustomerRawEvent event,
      DlqEvent dlqEvent,
      ErrorType errorType,
      int retryCount,
      String originalTopic,
      long offset) {

    String payload;
    try {
      payload = objectMapper.writeValueAsString(dlqEvent);
    } catch (JsonProcessingException ex) {
      log.error("Failed to serialize DLQ event for eventId={}: {}", event.getEventId(), ex.getMessage(), ex);
      payload = "{}";
    }

    Headers headers = buildDlqHeaders(event, errorType, retryCount, originalTopic, offset);
    String key = event.getNationalId() != null ? event.getNationalId() : UUID.randomUUID().toString();

    ProducerRecord<String, String> record = new ProducerRecord<>(dlqTopic, null, key, payload, headers);
    kafkaTemplate.send(record).whenComplete(
        (result, ex) -> {
          if (ex == null) {
            log.info("Sent to DLQ: topic={}, partition={}, offset={}, eventId={}, errorType={}",
                dlqTopic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset(),
                event.getEventId(), errorType);
          } else {
            log.error("Failed to send to DLQ: eventId={}, error={}", event.getEventId(), ex.getMessage(), ex);
          }
        });
  }

  private Headers buildDlqHeaders(
      CustomerRawEvent event,
      ErrorType errorType,
      int retryCount,
      String originalTopic,
      long offset) {

    Headers headers = new RecordHeaders();
    headers.add(HEADER_ORIGINAL_TOPIC, originalTopic.getBytes());
    headers.add(HEADER_OFFSET, serializeLong(offset));
    headers.add(HEADER_ERROR_TYPE, errorType.name().getBytes());
    headers.add(HEADER_ERROR_MESSAGE, truncateErrorMessage(event, 256));
    headers.add(HEADER_RETRY_COUNT, serializeInt(retryCount));
    headers.add(HEADER_TIMESTAMP, Instant.now().toString().getBytes());
    headers.add(HEADER_SCHEMA_VERSION, SCHEMA_VERSION.getBytes());
    return headers;
  }

  private byte[] truncateErrorMessage(CustomerRawEvent event, int maxLength) {
    String msg = event.getEventId() != null
        ? "EventId=" + event.getEventId()
        : "Unknown event";
    return msg.substring(0, Math.min(msg.length(), maxLength)).getBytes();
  }

  private byte[] serializeLong(long value) {
    return java.nio.ByteBuffer.allocate(8).putLong(value).array();
  }

  private byte[] serializeInt(int value) {
    return java.nio.ByteBuffer.allocate(4).putInt(value).array();
  }
}
