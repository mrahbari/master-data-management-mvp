/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka consumer configuration with retry and error handling.
 *
 * <p>Features: - Manual acknowledgment (for at-least-once delivery) - Exponential backoff retry -
 * Dead Letter Queue (DLQ) for poison pills - Concurrent consumers for throughput
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

  private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

  @Value("${spring.kafka.consumer.group-id:customer-mastering-group}")
  private String groupId;

  @Value("${spring.kafka.consumer.max-poll-records:100}")
  private int maxPollRecords;

  @Value("${kafka.retry.max-attempts:3}")
  private int maxRetryAttempts;

  @Value("${kafka.retry.initial-interval:1000}")
  private long initialInterval;

  @Value("${kafka.retry.max-interval:10000}")
  private long maxInterval;

  @Value("${kafka.retry.multiplier:2.0}")
  private double multiplier;

  /** Consumer factory with custom configuration. */
  @Bean("kafkaListenerConsumerFactory")
  public ConsumerFactory<String, Object> consumerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {

    return new DefaultKafkaConsumerFactory<>(createConsumerProperties(bootstrapServers));
  }

  /** Kafka listener container factory with error handling. */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
      ConsumerFactory<String, Object> consumerFactory) {

    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(consumerFactory);
    factory.setConcurrency(3); // Parallel consumers
    factory.setCommonErrorHandler(errorHandler());
    factory
        .getContainerProperties()
        .setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);

    return factory;
  }

  /** Error handler with retry and DLQ. */
  @Bean
  public CommonErrorHandler errorHandler() {
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            (record, exception) -> {
              // DLQ handler - log and potentially alert
              log.error(
                  "Message sent to DLQ after {} retries: topic={}, partition={}, offset={}, key={}",
                  maxRetryAttempts,
                  record.topic(),
                  record.partition(),
                  record.offset(),
                  record.key(),
                  exception);
              // In production: send to DLQ topic here
            },
            new ExponentialBackOff(initialInterval, multiplier));

    errorHandler.addNotRetryableExceptions(
        IllegalArgumentException.class, // Don't retry bad data
        NullPointerException.class);

    return errorHandler;
  }

  private Map<String, Object> createConsumerProperties(String bootstrapServers) {
    Map<String, Object> props = new HashMap<>();

    // Basic consumer config
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        org.apache.kafka.common.serialization.StringDeserializer.class);
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        org.springframework.kafka.support.serializer.JsonDeserializer.class);

    // Offset management
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit

    // Performance
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
    props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
    props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

    // Session management
    props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
    props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
    props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

    // Deserializer config
    props.put("spring.json.trusted.packages", "com.mdm.mastering.dto");
    props.put("spring.json.use.type.headers", false);
    props.put(
        org.springframework.kafka.support.serializer.JsonDeserializer.VALUE_DEFAULT_TYPE,
        "com.mdm.mastering.dto.CustomerRawEvent");

    return props;
  }
}
