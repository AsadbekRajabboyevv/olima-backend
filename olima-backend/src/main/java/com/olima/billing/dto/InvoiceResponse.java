package com.olima.billing.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
    UUID organizationId,
    String organizationName,
    YearMonth month,
    long requests,
    long promptTokens,
    long completionTokens,
    long totalTokens,
    BigDecimal costUzs,
    TokenRateResponse currentRate,
    List<DailyCost> days) {}
