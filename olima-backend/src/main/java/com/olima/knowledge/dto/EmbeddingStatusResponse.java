package com.olima.knowledge.dto;

import java.util.UUID;

public record EmbeddingStatusResponse(
    UUID organizationId,
    boolean embeddingEnabled,
    boolean modelConfigured,
    boolean vectorStoreAvailable,
    String model,
    long totalChunks,
    long embeddedChunks,
    long pendingChunks) {}
