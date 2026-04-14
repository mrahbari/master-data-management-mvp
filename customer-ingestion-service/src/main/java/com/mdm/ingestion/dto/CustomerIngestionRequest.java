/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.ingestion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerIngestionRequest {

  @NotBlank(message = "nationalId is required")
  private String nationalId;

  private String name;

  @Email(message = "Invalid email format")
  private String email;

  private String phone;

  @NotBlank(message = "Source system is required")
  private String sourceSystem;
}
