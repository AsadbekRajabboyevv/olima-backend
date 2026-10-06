package com.olima.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrganizationBillingRow(
    UUID organizationId,
    String organizationName,
    long requests,
    long totalTokens,
    BigDecimal costUzs,
    boolean customRate) {}
