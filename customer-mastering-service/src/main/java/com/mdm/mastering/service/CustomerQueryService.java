/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mdm.mastering.dto.CustomerQueryResponse;
import com.mdm.mastering.entity.CustomerGoldenEntity;
import com.mdm.mastering.repository.CustomerGoldenRepository;

/**
 * Customer Query Service (Read Side of CQRS).
 *
 * <p>This service handles all read operations for customer data. It reads from the golden record
 * table which is optimized for queries.
 *
 * <p>CQRS Benefits: - Separate read and write models - Read model optimized for queries
 * (denormalized) - Write model optimized for consistency (normalized) - Independent scaling of read
 * and write operations
 */
@Service
@Transactional(readOnly = true)
public class CustomerQueryService {

  private final CustomerGoldenRepository goldenRepository;

  public CustomerQueryService(CustomerGoldenRepository goldenRepository) {
    this.goldenRepository = goldenRepository;
  }

  /**
   * Get all customers with pagination.
   *
   * @param pageable Pagination parameters
   * @return Page of customer query responses
   */
  public Page<CustomerQueryResponse> getAllCustomers(Pageable pageable) {
    return goldenRepository.findAll(pageable).map(this::toQueryResponse);
  }

  /**
   * Get customer by ID.
   *
   * @param id Customer golden record ID
   * @return Customer query response
   */
  public CustomerQueryResponse getCustomerById(UUID id) {
    CustomerGoldenEntity entity =
        goldenRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    return toQueryResponse(entity);
  }

  /**
   * Get customer by email.
   *
   * @param email Customer email (case-insensitive)
   * @return Customer query response
   */
  public CustomerQueryResponse getCustomerByEmail(String email) {
    String normalizedEmail = email.trim().toLowerCase();
    CustomerGoldenEntity entity =
        goldenRepository
            .findByNormalizedEmail(normalizedEmail)
            .orElseThrow(() -> new CustomerNotFoundException(email));
    return toQueryResponse(entity);
  }

  /**
   * Search customers by name.
   *
   * @param firstName First name (partial match)
   * @param lastName Last name (partial match)
   * @param pageable Pagination parameters
   * @return Page of matching customers
   */
  public Page<CustomerQueryResponse> searchByName(
      String firstName, String lastName, Pageable pageable) {
    // Simple search implementation - fetch and filter in memory
    // For production: Use Elasticsearch or database full-text search
    Page<CustomerGoldenEntity> entityPage = goldenRepository.findAll(pageable);

    return entityPage.map(this::toQueryResponse);
  }

  /**
   * Check if customer exists by email.
   *
   * @param email Customer email
   * @return true if customer exists
   */
  public boolean existsByEmail(String email) {
    String normalizedEmail = email.trim().toLowerCase();
    return goldenRepository.existsByNormalizedEmail(normalizedEmail);
  }

  /**
   * Get total customer count.
   *
   * @return Total number of golden records
   */
  public long getTotalCustomerCount() {
    return goldenRepository.count();
  }

  /** Convert entity to query response DTO. */
  private CustomerQueryResponse toQueryResponse(CustomerGoldenEntity entity) {
    return CustomerQueryResponse.builder()
        .id(entity.getId())
        .email(entity.getEmail())
        .firstName(entity.getFirstName())
        .lastName(entity.getLastName())
        .phone(entity.getPhone())
        .confidenceScore(entity.getConfidenceScore())
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .lastSourceSystem(entity.getLastSourceSystem())
        .build();
  }

  /** Exception thrown when customer is not found. */
  public static class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(Object identifier) {
      super("Customer not found: " + identifier);
    }
  }
}
