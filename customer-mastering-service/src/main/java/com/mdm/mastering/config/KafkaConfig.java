/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.mdm.mastering.dto.CustomerMasteredEvent;
import com.mdm.mastering.dto.CustomerRawEvent;

@Configuration
public class KafkaConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${kafka.topics.customer-raw}")
  private String customerRawTopic;

  @Value("${kafka.topics.customer-mastered}")
  private String customerMasteredTopic;

  @Bean
  public NewTopic customerRawTopic() {
    return TopicBuilder.name(customerRawTopic).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic customerMasteredTopic() {
    return TopicBuilder.name(customerMasteredTopic).partitions(3).replicas(1).build();
  }

  @Bean
  public ConsumerFactory<String, CustomerRawEvent> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(
        org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        bootstrapServers);
    props.put(
        org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG,
        "customer-mastering-group");
    props.put(
        org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class);
    props.put(
        org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        JsonDeserializer.class);
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mdm.mastering.dto");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    props.put(
        org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ProducerFactory<String, CustomerMasteredEvent> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(
        org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
        bootstrapServers);
    props.put(
        org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class);
    props.put(
        org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        JsonSerializer.class);
    props.put(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG, "all");
    props.put(org.apache.kafka.clients.producer.ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, CustomerMasteredEvent> kafkaTemplate(
      ProducerFactory<String, CustomerMasteredEvent> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean
  public ProducerFactory<String, String> stringProducerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(
        org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
        bootstrapServers);
    props.put(
        org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class);
    props.put(
        org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class);
    props.put(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG, "all");
    props.put(org.apache.kafka.clients.producer.ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, String> stringKafkaTemplate(
      ProducerFactory<String, String> stringProducerFactory) {
    return new KafkaTemplate<>(stringProducerFactory);
  }
}
