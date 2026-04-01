/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Test JWT Token Info for local development.
 *
 * <p>⚠️ DO NOT USE IN PRODUCTION - For demo purposes only!
 *
 * <p>In production, use a real OAuth2 provider: - Auth0 - AWS Cognito - Keycloak - Okta
 */
@Configuration
@Profile("!production")
public class TestTokenInfo {

  @Value("${oauth2.test.issuer:https://mdm-demo.auth0.com/}")
  private String issuer;

  @Value("${oauth2.test.audience:mdm-api}")
  private String audience;

  /** Print test token information to console on startup. */
  @Bean
  @ConditionalOnProperty(name = "oauth2.test.enabled", havingValue = "true")
  public TestTokenPrinter testTokenPrinter() {
    return new TestTokenPrinter(issuer, audience);
  }

  static class TestTokenPrinter {
    private final String issuer;
    private final String audience;

    TestTokenPrinter(String issuer, String audience) {
      this.issuer = issuer;
      this.audience = audience;
      printTestTokenInfo();
    }

    private void printTestTokenInfo() {
      System.out.println("\n=== OAUTH2 TEST MODE ENABLED ===");
      System.out.println("Issuer: " + issuer);
      System.out.println("Audience: " + audience);
      System.out.println("");
      System.out.println("To generate test tokens, use one of these options:");
      System.out.println("");
      System.out.println("1. Auth0 Test Tokens:");
      System.out.println(
          "   https://auth0.com/docs/quickstart/spa/react/02-calling-an-api#test-your-api");
      System.out.println("");
      System.out.println("2. JWT.io Token Generator:");
      System.out.println("   https://jwt.io/");
      System.out.println("");
      System.out.println("3. Use a mock OAuth2 server:");
      System.out.println("   docker run -p 8080:8080 ghcr.io/navikt/mock-oauth2-server:latest");
      System.out.println("");
      System.out.println("Example JWT Payload:");
      System.out.println("{");
      System.out.println("  \"iss\": \"" + issuer + "\",");
      System.out.println("  \"sub\": \"user123\",");
      System.out.println("  \"aud\": \"" + audience + "\",");
      System.out.println("  \"iat\": <current_timestamp>,");
      System.out.println("  \"exp\": <current_timestamp + 3600>,");
      System.out.println("  \"roles\": [\"CUSTOMER_WRITE\", \"ADMIN\"]");
      System.out.println("}");
      System.out.println("=====================================\n");
    }
  }
}
