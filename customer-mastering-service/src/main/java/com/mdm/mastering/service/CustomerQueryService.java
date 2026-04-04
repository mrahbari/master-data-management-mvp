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
 * <p>CQRS Benefits:
 *
 * <ul>
 *   <li>Separate read and write models
 *   <li>Read model optimized for queries (denormalized)
 *   <li>Write model optimized for consistency (normalized)
 *   <li>Independent scaling of read and write operations
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class CustomerQueryService {

  private final CustomerGoldenRepository goldenRepository;

  public CustomerQueryService(CustomerGoldenRepository goldenRepository) {
    this.goldenRepository = goldenRepository;
  }

  /** Get all customers with pagination. */
  public Page<CustomerQueryResponse> getAllCustomers(Pageable pageable) {
    return goldenRepository.findAll(pageable).map(this::toQueryResponse);
  }

  /** Get customer by ID. */
  public CustomerQueryResponse getCustomerById(UUID id) {
    CustomerGoldenEntity entity =
        goldenRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    return toQueryResponse(entity);
  }

  /** Get customer by nationalId. */
  public CustomerQueryResponse getCustomerByNationalId(String nationalId) {
    String normalizedId = normalizeNationalId(nationalId);
    CustomerGoldenEntity entity =
        goldenRepository
            .findByNationalId(normalizedId)
            .orElseThrow(() -> new CustomerNotFoundException(nationalId));
    return toQueryResponse(entity);
  }

  /** Search customers by name. */
  public Page<CustomerQueryResponse> searchByName(String name, Pageable pageable) {
    Page<CustomerGoldenEntity> entityPage = goldenRepository.findAll(pageable);
    return entityPage.map(this::toQueryResponse);
  }

  /** Check if customer exists by nationalId. */
  public boolean existsByNationalId(String nationalId) {
    String normalizedId = normalizeNationalId(nationalId);
    return goldenRepository.existsByNationalId(normalizedId);
  }

  /** Get total customer count. */
  public long getTotalCustomerCount() {
    return goldenRepository.count();
  }

  /** Convert entity to query response DTO. */
  private CustomerQueryResponse toQueryResponse(CustomerGoldenEntity entity) {
    return CustomerQueryResponse.builder()
        .id(entity.getId())
        .nationalId(entity.getNationalId())
        .name(entity.getName())
        .email(entity.getEmail())
        .phone(entity.getPhone())
        .confidenceScore(entity.getConfidenceScore())
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .lastSourceSystem(entity.getLastSourceSystem())
        .build();
  }

  private static String normalizeNationalId(String nationalId) {
    if (nationalId == null) {
      return null;
    }
    return nationalId.trim().replaceAll("[^a-zA-Z0-9]", "");
  }

  /** Exception thrown when customer is not found. */
  public static class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(Object identifier) {
      super("Customer not found: " + identifier);
    }
  }
}
