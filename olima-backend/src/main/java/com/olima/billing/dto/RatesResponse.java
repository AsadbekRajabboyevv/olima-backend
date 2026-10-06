package com.olima.billing.dto;

import java.util.List;

public record RatesResponse(
    TokenRateResponse defaultRate, List<TokenRateResponse> organizationRates) {}
