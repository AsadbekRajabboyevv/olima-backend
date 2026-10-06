package com.olima.usage.dto;

import java.util.UUID;

public record UsageResponse(
    UUID organizationId,
    long requests,
    long promptTokens,
    long completionTokens,
    long totalTokens,
    long monthlyQuota) {}
