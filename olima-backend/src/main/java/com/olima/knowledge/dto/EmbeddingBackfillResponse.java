package com.olima.knowledge.dto;

public record EmbeddingBackfillResponse(
    int processed, int failed, long remaining, boolean embeddingEnabled) {}
