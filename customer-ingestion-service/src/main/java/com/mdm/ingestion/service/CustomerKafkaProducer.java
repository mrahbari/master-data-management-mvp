/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import com.mdm.ingestion.util.SensitiveDataMasker;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import com.mdm.ingestion.dto.CustomerRawEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class CustomerKafkaProducer {

  private final KafkaTemplate<String, CustomerRawEvent> kafkaTemplate;

  @Value("${kafka.topics.customer-raw}")
  private String topic;

  /**
   * Publishes a customer event to Kafka.
   *
   * @param event the event to publish
   * @param partitionKey the partition key (normalized nationalId) for ordering guarantees
   * @param eventVersion the event version for schema evolution
   * @return future result of the send operation
   */
  public CompletableFuture<SendResult<String, CustomerRawEvent>> send(
      CustomerRawEvent event, String partitionKey, String eventVersion) {
    String key = partitionKey.toLowerCase().trim();

    Headers headers = buildHeaders(event, partitionKey, eventVersion);

    ProducerRecord<String, CustomerRawEvent> record =
        new ProducerRecord<>(topic, null, key, event, headers);

    log.debug(
        "Publishing event to Kafka topic={}, partitionKey={}, eventId={}",
        topic,
        maskKey(key),
        event.getEventId());

    return kafkaTemplate.send(record);
  }

  private String maskKey(String key) {
      return SensitiveDataMasker.maskHash(key);
  }

  private Headers buildHeaders(CustomerRawEvent event, String idempotencyKey, String eventVersion) {
    Headers headers = new RecordHeaders();
    headers.add(header("X-Event-ID", event.getEventId().toString()));
    headers.add(header("X-Idempotency-Key", idempotencyKey));
    headers.add(header("X-Source-System", event.getSourceSystem()));
    headers.add(header("X-Timestamp", event.getTimestamp().toString()));
    headers.add(header("X-Event-Version", eventVersion));
    return headers;
  }

  private Header header(String key, String value) {
    return new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8));
  }
}
