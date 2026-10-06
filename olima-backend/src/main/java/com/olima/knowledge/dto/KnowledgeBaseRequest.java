package com.olima.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record KnowledgeBaseRequest(
    UUID organizationId,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 2000) String description) {}
