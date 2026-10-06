package com.olima.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TokenRateRequest(
    @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal inputPricePerMillion,
    @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2)
        BigDecimal outputPricePerMillion) {}
