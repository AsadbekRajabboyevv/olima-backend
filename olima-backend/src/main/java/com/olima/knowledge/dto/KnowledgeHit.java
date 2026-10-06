package com.olima.knowledge.dto;

import java.util.UUID;

/**
 * Bilimlar bazasi qidiruvining bitta natijasi — knowledge modulidan tashqariga chiqadigan shakl.
 * JPA entity ({@code DocumentChunkEntity}) modul ichida qoladi.
 */
public record KnowledgeHit(UUID id, UUID documentId, String content, String sourceUrl) {}
