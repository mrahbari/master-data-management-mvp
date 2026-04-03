/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mdm.mastering.entity.CustomerGoldenEntity;

@Repository
public interface CustomerGoldenRepository extends JpaRepository<CustomerGoldenEntity, UUID> {

  Optional<CustomerGoldenEntity> findByNationalId(String nationalId);

  boolean existsByNationalId(String nationalId);
}
