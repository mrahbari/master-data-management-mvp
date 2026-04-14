/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** General infrastructure configuration. */
@Configuration
public class InfrastructureConfig {

  /** Provides a system Clock bean for deterministic time-based testing. */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
