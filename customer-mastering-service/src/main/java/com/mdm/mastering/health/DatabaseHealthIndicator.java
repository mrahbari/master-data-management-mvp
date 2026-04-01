/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.health;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom database health indicator with query latency check.
 *
 * <p>Checks: - Connection pool availability - Query response time - Table accessibility
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

  private static final Logger log = LoggerFactory.getLogger(DatabaseHealthIndicator.class);
  private static final long QUERY_TIMEOUT_MS = 1000;

  private final DataSource dataSource;

  public DatabaseHealthIndicator(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Health health() {
    long startTime = System.currentTimeMillis();

    try (Connection conn = dataSource.getConnection()) {
      // Check connection pool
      if (conn == null) {
        return Health.down()
            .withDetail("issue", "Could not obtain database connection")
            .withDetail("pool", "exhausted")
            .build();
      }

      // Check table accessibility and get row counts
      long rawCount = executeCountQuery(conn, "customer_raw");
      long goldenCount = executeCountQuery(conn, "customer_golden");

      long queryTime = System.currentTimeMillis() - startTime;

      if (queryTime > QUERY_TIMEOUT_MS) {
        log.warn("Database query took {}ms (threshold: {}ms)", queryTime, QUERY_TIMEOUT_MS);
        return Health.unknown()
            .withDetail("issue", "Slow database response")
            .withDetail("queryTimeMs", queryTime)
            .withDetail("thresholdMs", QUERY_TIMEOUT_MS)
            .build();
      }

      return Health.up()
          .withDetail("status", "healthy")
          .withDetail("queryTimeMs", queryTime)
          .withDetail("rawTableRows", rawCount)
          .withDetail("goldenTableRows", goldenCount)
          .build();

    } catch (Exception ex) {
      log.error("Database health check failed", ex);
      return Health.down(ex).withDetail("issue", "Database connection failed").build();
    }
  }

  private long executeCountQuery(Connection conn, String tableName) {
    try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName)) {
      stmt.setQueryTimeout((int) (QUERY_TIMEOUT_MS / 1000));
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    } catch (Exception ex) {
      log.warn("Count query failed for table {}: {}", tableName, ex.getMessage());
      return -1;
    }
    return -1;
  }
}
