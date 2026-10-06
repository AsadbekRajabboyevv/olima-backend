package com.olima.organization.dto;

import jakarta.validation.constraints.Size;

public record WidgetSettingsRequest(@Size(max = 300) String greeting, boolean greetingEnabled) {}
