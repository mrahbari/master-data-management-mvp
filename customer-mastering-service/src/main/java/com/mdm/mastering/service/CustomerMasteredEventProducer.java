/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.mdm.mastering.dto.CustomerMasteredEvent;

@Service
public class CustomerMasteredEventProducer {

  private static final Logger log = LoggerFactory.getLogger(CustomerMasteredEventProducer.class);

  private final KafkaTemplate<String, CustomerMasteredEvent> kafkaTemplate;
  private final String topic;

  public CustomerMasteredEventProducer(
      KafkaTemplate<String, CustomerMasteredEvent> kafkaTemplate,
      @org.springframework.beans.factory.annotation.Value("${kafka.topics.customer-mastered}")
          String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  public void publish(CustomerMasteredEvent event) {
    String key = event.getGoldenRecordId().toString();

    log.debug(
        "Publishing mastered event to Kafka topic={}, key={}, action={}",
        topic,
        key,
        event.getAction());

    kafkaTemplate
        .send(topic, key, event)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                log.error(
                    "Failed to publish mastered event: eventId={}, error={}",
                    event.getEventId(),
                    ex.getMessage());
              } else {
                log.info(
                    "Successfully published mastered event: eventId={}, goldenRecordId={}, action={}",
                    event.getEventId(),
                    event.getGoldenRecordId(),
                    event.getAction());
              }
            });
  }
}
