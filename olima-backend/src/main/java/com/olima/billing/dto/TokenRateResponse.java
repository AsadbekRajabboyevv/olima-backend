package com.olima.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TokenRateResponse(
    UUID organizationId,
    String organizationName,
    BigDecimal inputPricePerMillion,
    BigDecimal outputPricePerMillion,
    boolean custom,
    String updatedBy,
    Instant updatedAt) {}
