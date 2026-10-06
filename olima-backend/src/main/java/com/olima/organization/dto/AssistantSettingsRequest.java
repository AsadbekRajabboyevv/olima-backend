package com.olima.organization.dto;

import jakarta.validation.constraints.NotNull;

public record AssistantSettingsRequest(@NotNull Boolean webSearchEnabled) {}
