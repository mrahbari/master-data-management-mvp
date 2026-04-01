/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

  @Value("${kafka.topics.customer-raw}")
  private String customerRawTopic;

  @Bean
  public NewTopic customerRawTopic() {
    return TopicBuilder.name(customerRawTopic).partitions(3).replicas(1).build();
  }
}
