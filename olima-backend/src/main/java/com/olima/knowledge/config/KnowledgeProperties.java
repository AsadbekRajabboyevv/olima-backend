package com.olima.knowledge.config;

import com.olima.execution.search.Search;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.knowledge")
public record KnowledgeProperties(
    @Valid @NotNull @DefaultValue Ingestion ingestion,
    @Valid @NotNull @DefaultValue Storage storage,
    @Valid @NotNull @DefaultValue Chunking chunking,
    @Valid @NotNull @DefaultValue Embedding embedding,
    @Valid @NotNull @DefaultValue Search search) {

  public record Ingestion(
      @NotNull @DefaultValue("25MB") DataSize maxDownloadSize,
      @Min(1000) @DefaultValue("5000000") int maxExtractedChars,
      @NotNull @DefaultValue("2m") Duration parseTimeout,
      @DefaultValue({
            "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "html", "htm", "xls", "xlsx", "csv",
            "ppt", "pptx"
          })
          List<String> allowedExtensions,
      @DefaultValue("true") boolean rejectDuplicates,
      @Valid @NotNull @DefaultValue Worker worker) {}

  public record Worker(
      @DefaultValue("true") boolean enabled,
      @NotNull @DefaultValue("5s") Duration pollInterval,
      @Min(1) @DefaultValue("2") int concurrency,
      @Min(1) @DefaultValue("3") int maxAttempts,
      @NotNull @DefaultValue("15m") Duration staleAfter) {}

  public record Storage(@NotBlank @DefaultValue("./data/documents") String path) {}

  public record Chunking(
      @Min(200) @DefaultValue("1500") int size, @Min(0) @DefaultValue("150") int overlap) {}

  public record Embedding(
      @Min(1) @DefaultValue("1536") int dimensions,
      @Min(1) @DefaultValue("64") int batchSize,
      @Min(100) @DefaultValue("24000") int maxInputChars,
      @Min(1) @DefaultValue("500") int backfillMaxLimit) {}

  public record Search(
      @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.25") double minSimilarity,
      @Min(1) @DefaultValue("60") int rrfK,
      @Min(1) @DefaultValue("3") int candidateMultiplier,
      @Min(1) @DefaultValue("3") int minTermLength,
      @Min(10) @DefaultValue("500") int maxQueryChars) {}
}
