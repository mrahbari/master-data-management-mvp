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
 *
 * <p>Uses a dedicated health-check topic to avoid polluting the customer.raw topic
 * with non-JSON messages that would cause deserialization errors.
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

  private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);
  private static final long PRODUCE_TIMEOUT_MS = 3000;
  private static final String HEALTH_CHECK_TOPIC = "health-check";

  private final KafkaTemplate<String, String> kafkaTemplate;

  public KafkaHealthIndicator(KafkaTemplate<String, String> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @Override
  public Health health() {
    long startTime = System.currentTimeMillis();

    try {
      // Test produce connectivity with a lightweight message
      // Use a dedicated health-check topic to avoid polluting customer.raw
      CompletableFuture future = kafkaTemplate.send(HEALTH_CHECK_TOPIC, "__health-check__", "health");

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
