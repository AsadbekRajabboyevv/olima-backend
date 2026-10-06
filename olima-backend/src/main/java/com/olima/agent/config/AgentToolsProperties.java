package com.olima.agent.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.agent.tools")
public record AgentToolsProperties(
    @Valid @NotNull Knowledge knowledge,
    @Valid @NotNull WebSearch webSearch,
    @Valid @NotNull FetchPage fetchPage,
    @Valid @NotNull Complaint complaint,
    @Valid @NotNull ModelMessages messages,
    @Valid @NotNull SourceLabels sourceLabels,
    @Valid @NotNull SourcePolicy sourcePolicy,
    @Min(50) @Max(5000) int toolCallPreviewChars) {

  public static final String ORG_PLACEHOLDER = "{org}";

  public static String render(String template, String orgName) {
    return template.replace(ORG_PLACEHOLDER, orgName != null ? orgName : "");
  }

  public record Knowledge(
      @Min(1) @Max(20) int maxResults,
      @NotBlank String description,
      @NotBlank String queryDescription,
      @NotBlank String emptyResultMessage) {}

  public record WebSearch(
      boolean enabled, @NotBlank String description, @NotBlank String queryDescription) {}

  public record FetchPage(
      boolean enabled,
      @NotBlank String description,
      @NotBlank String urlDescription,
      @NotBlank String blockedMessage) {}

  public record Complaint(
      @NotBlank String defaultSubject,
      @NotBlank String defaultCategory,
      @NotBlank String draftCreatedMessage,
      @NotBlank String toolCallStatus) {}

  public record ModelMessages(
      @NotBlank String toolFailed,
      @NotBlank String noData,
      @NotBlank String nextStepWithWebSearch,
      @NotBlank String nextStepInternalOnly) {}

  public record SourceLabels(
      @NotBlank String organizationDocument,
      @NotBlank String genericDocument,
      @NotNull List<String> genericTitles) {}

  public record SourcePolicy(@NotBlank String withWebSearch, @NotBlank String internalOnly) {}
}
