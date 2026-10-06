package com.olima.tool.dto;

import com.olima.tool.enums.AccessLevel;
import com.olima.tool.enums.ToolType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ToolRequest(
    @NotBlank
        @Pattern(
            regexp = "^[a-zA-Z0-9_-]{1,64}$",
            message = "faqat lotin harflari, raqam, _ va - (64 belgigacha)")
        String name,
    @NotBlank @Size(max = 2000) String description,
    @NotNull ToolType type,
    String configuration,
    Boolean requiresConfirmation,
    AccessLevel accessLevel,
    Boolean enabled,
    List<@Valid ToolParameterRequest> parameters) {}
