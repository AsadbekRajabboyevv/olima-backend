package com.olima.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record OrganizationRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 4000) String description,
    @PositiveOrZero Long monthlyTokenQuota) {}
