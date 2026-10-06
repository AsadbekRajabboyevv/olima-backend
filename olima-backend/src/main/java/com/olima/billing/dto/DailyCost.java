package com.olima.billing.dto;

import java.math.BigDecimal;

public record DailyCost(String day, long requests, long tokens, BigDecimal costUzs) {}
