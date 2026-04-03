/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mdm.mastering.entity.CustomerRawEntity;

@Repository
public interface CustomerRawRepository extends JpaRepository<CustomerRawEntity, UUID> {

  Optional<CustomerRawEntity> findByEventId(UUID eventId);

  boolean existsByEventId(UUID eventId);
}
