/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mdm.mastering.dto.CustomerQueryResponse;
import com.mdm.mastering.service.CustomerQueryService;

/**
 * Customer Query Controller (Read Side of CQRS).
 *
 * <p>This controller handles all read/query operations for customer data. It uses the
 * CustomerQueryService to fetch data from the golden record table.
 *
 * <p>CQRS Pattern: - Command Side: Customer Ingestion Service (writes to Kafka) - Query Side: This
 * controller (reads from PostgreSQL)
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerQueryController {

  private final CustomerQueryService queryService;

  public CustomerQueryController(CustomerQueryService queryService) {
    this.queryService = queryService;
  }

  /**
   * Get all customers with pagination.
   *
   * <p>GET /api/customers?page=0&size=20&sort=updatedAt,desc
   *
   * @param page Page number (0-indexed)
   * @param size Page size
   * @param sort Sort field and direction
   * @return Page of customers
   */
  @GetMapping
  public ResponseEntity<Page<CustomerQueryResponse>> getAllCustomers(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort) {

    String[] sortParams = sort.split(",");
    Sort.Direction direction =
        sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc")
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    String sortBy = sortParams[0];

    Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
    Page<CustomerQueryResponse> customers = queryService.getAllCustomers(pageable);

    return ResponseEntity.ok(customers);
  }

  /**
   * Get customer by ID.
   *
   * <p>GET /api/customers/{id}
   *
   * @param id Customer golden record ID
   * @return Customer details
   */
  @GetMapping("/{id}")
  public ResponseEntity<CustomerQueryResponse> getCustomerById(@PathVariable UUID id) {
    CustomerQueryResponse customer = queryService.getCustomerById(id);
    return ResponseEntity.ok(customer);
  }

  /**
   * Get customer by email.
   *
   * <p>GET /api/customers/by-email?email=john@example.com
   *
   * @param email Customer email (case-insensitive)
   * @return Customer details
   */
  @GetMapping("/by-email")
  public ResponseEntity<CustomerQueryResponse> getCustomerByEmail(@RequestParam String email) {
    CustomerQueryResponse customer = queryService.getCustomerByEmail(email);
    return ResponseEntity.ok(customer);
  }

  /**
   * Search customers by name.
   *
   * <p>GET /api/customers/search?firstName=John&lastName=Doe&page=0&size=20
   *
   * @param firstName First name (partial match)
   * @param lastName Last name (partial match)
   * @param page Page number
   * @param size Page size
   * @return Page of matching customers
   */
  @GetMapping("/search")
  public ResponseEntity<Page<CustomerQueryResponse>> searchCustomers(
      @RequestParam(required = false) String firstName,
      @RequestParam(required = false) String lastName,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    Pageable pageable = PageRequest.of(page, size);
    Page<CustomerQueryResponse> customers =
        queryService.searchByName(firstName, lastName, pageable);
    return ResponseEntity.ok(customers);
  }

  /**
   * Check if customer exists by email.
   *
   * <p>GET /api/customers/exists?email=john@example.com
   *
   * @param email Customer email
   * @return Existence check result
   */
  @GetMapping("/exists")
  public ResponseEntity<Map<String, Object>> checkCustomerExists(@RequestParam String email) {
    boolean exists = queryService.existsByEmail(email);

    Map<String, Object> response = new HashMap<>();
    response.put("email", email);
    response.put("exists", exists);

    return ResponseEntity.ok(response);
  }

  /**
   * Get total customer count.
   *
   * <p>GET /api/customers/count
   *
   * @return Total customer count
   */
  @GetMapping("/count")
  public ResponseEntity<Map<String, Object>> getCustomerCount() {
    long count = queryService.getTotalCustomerCount();

    Map<String, Object> response = new HashMap<>();
    response.put("totalCustomers", count);

    return ResponseEntity.ok(response);
  }
}
