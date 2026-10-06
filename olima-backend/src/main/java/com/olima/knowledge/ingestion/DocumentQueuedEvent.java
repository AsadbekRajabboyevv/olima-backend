package com.olima.knowledge.ingestion;

import java.util.UUID;

public record DocumentQueuedEvent(UUID documentId) {}
