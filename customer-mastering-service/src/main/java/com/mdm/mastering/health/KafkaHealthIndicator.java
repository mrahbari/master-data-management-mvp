/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.health;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka health indicator with connectivity and latency check.
 *
 * <p>Checks: - Broker connectivity - Produce latency - Topic existence
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

  private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);
  private static final long PRODUCE_TIMEOUT_MS = 3000;

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final String testTopic;

  public KafkaHealthIndicator(
      KafkaTemplate<String, String> kafkaTemplate,
      @org.springframework.beans.factory.annotation.Value(
              "${kafka.topics.customer-raw:customer.raw}")
          String testTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.testTopic = testTopic;
  }

  @Override
  public Health health() {
    long startTime = System.currentTimeMillis();

    try {
      // Test produce connectivity with a lightweight message
      CompletableFuture future = kafkaTemplate.send(testTopic, "__health-check__", "health");

      try {
        future.get(PRODUCE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        long produceTime = System.currentTimeMillis() - startTime;

        if (produceTime > PRODUCE_TIMEOUT_MS / 2) {
          log.warn("Kafka produce took {}ms (slow broker)", produceTime);
          return Health.unknown()
              .withDetail("issue", "Slow Kafka broker response")
              .withDetail("produceTimeMs", produceTime)
              .build();
        }

        return Health.up()
            .withDetail("status", "healthy")
            .withDetail("produceTimeMs", produceTime)
            .withDetail(
                "bootstrapServers",
                kafkaTemplate
                    .getProducerFactory()
                    .getConfigurationProperties()
                    .get("bootstrap.servers"))
            .build();

      } catch (Exception ex) {
        log.error("Kafka produce failed", ex);
        return Health.down(ex).withDetail("issue", "Kafka produce failed").build();
      }

    } catch (Exception ex) {
      log.error("Kafka health check failed", ex);
      return Health.down(ex).withDetail("issue", "Kafka connectivity failed").build();
    }
  }
}
