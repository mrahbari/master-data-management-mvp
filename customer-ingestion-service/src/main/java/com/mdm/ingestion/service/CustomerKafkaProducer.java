/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.service;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import com.mdm.ingestion.dto.CustomerRawEvent;

@Service
public class CustomerKafkaProducer {

  private static final Logger log = LoggerFactory.getLogger(CustomerKafkaProducer.class);

  private final KafkaTemplate<String, CustomerRawEvent> kafkaTemplate;
  private final String topic;

  public CustomerKafkaProducer(
      KafkaTemplate<String, CustomerRawEvent> kafkaTemplate,
      @org.springframework.beans.factory.annotation.Value("${kafka.topics.customer-raw}")
          String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  public CompletableFuture<SendResult<String, CustomerRawEvent>> send(CustomerRawEvent event) {
    // Key by normalized email for partition ordering
    String key = event.getEmail().toLowerCase().trim();

    log.debug("Publishing event to Kafka topic={}, key={}, eventId={}", topic, key, event.getEventId());

    return kafkaTemplate.send(topic, key, event);
  }
}
