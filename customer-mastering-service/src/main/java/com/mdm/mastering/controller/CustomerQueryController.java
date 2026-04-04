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
 * <p>CQRS Pattern:
 *
 * <ul>
 *   <li>Command Side: Customer Ingestion Service (writes to Kafka)
 *   <li>Query Side: This controller (reads from PostgreSQL)
 * </ul>
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
   */
  @GetMapping("/{id}")
  public ResponseEntity<CustomerQueryResponse> getCustomerById(@PathVariable UUID id) {
    CustomerQueryResponse customer = queryService.getCustomerById(id);
    return ResponseEntity.ok(customer);
  }

  /**
   * Get customer by national ID.
   *
   * <p>GET /api/customers/by-national-id?nationalId=123456789012
   */
  @GetMapping("/by-national-id")
  public ResponseEntity<CustomerQueryResponse> getCustomerByNationalId(
      @RequestParam String nationalId) {
    CustomerQueryResponse customer = queryService.getCustomerByNationalId(nationalId);
    return ResponseEntity.ok(customer);
  }

  /**
   * Search customers by name.
   *
   * <p>GET /api/customers/search?name=John&page=0&size=20
   */
  @GetMapping("/search")
  public ResponseEntity<Page<CustomerQueryResponse>> searchCustomers(
      @RequestParam(required = false) String name,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    Pageable pageable = PageRequest.of(page, size);
    Page<CustomerQueryResponse> customers = queryService.searchByName(name, pageable);
    return ResponseEntity.ok(customers);
  }

  /**
   * Check if customer exists by national ID.
   *
   * <p>GET /api/customers/exists?nationalId=123456789012
   */
  @GetMapping("/exists")
  public ResponseEntity<Map<String, Object>> checkCustomerExists(@RequestParam String nationalId) {
    boolean exists = queryService.existsByNationalId(nationalId);

    Map<String, Object> response = new HashMap<>();
    response.put("nationalId", nationalId);
    response.put("exists", exists);

    return ResponseEntity.ok(response);
  }

  /**
   * Get total customer count.
   *
   * <p>GET /api/customers/count
   */
  @GetMapping("/count")
  public ResponseEntity<Map<String, Object>> getCustomerCount() {
    long count = queryService.getTotalCustomerCount();

    Map<String, Object> response = new HashMap<>();
    response.put("totalCustomers", count);

    return ResponseEntity.ok(response);
  }
}
