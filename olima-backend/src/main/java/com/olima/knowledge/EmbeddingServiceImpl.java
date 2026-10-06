package com.olima.knowledge;

import com.olima.knowledge.config.KnowledgeProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

  private final EmbeddingModel embeddingModel;
  private final JdbcTemplate jdbcTemplate;
  private final int dimensions;
  private final int batchSize;
  private final int maxInputChars;
  private final String modelName;
  private final boolean apiKeyConfigured;

  private volatile Boolean vectorStoreAvailable;

  public EmbeddingServiceImpl(
      ObjectProvider<EmbeddingModel> embeddingModel,
      JdbcTemplate jdbcTemplate,
      KnowledgeProperties properties,
      @Value("${spring.ai.openai.embedding.options.model:unknown}") String modelName,
      @Value("${spring.ai.openai.api-key:}") String apiKey) {
    this.embeddingModel = embeddingModel.getIfAvailable();
    this.jdbcTemplate = jdbcTemplate;
    this.dimensions = properties.embedding().dimensions();
    this.batchSize = properties.embedding().batchSize();
    this.maxInputChars = properties.embedding().maxInputChars();
    this.modelName = modelName;
    this.apiKeyConfigured = apiKey != null && !apiKey.isBlank();
  }

  @Override
  public boolean isEnabled() {
    return isModelConfigured() && isVectorStoreAvailable();
  }

  @Override
  public boolean isModelConfigured() {
    return embeddingModel != null && apiKeyConfigured;
  }

  @Override
  public boolean isVectorStoreAvailable() {
    Boolean available = vectorStoreAvailable;
    if (available == null) {
      available = detectVectorColumn();
      vectorStoreAvailable = available;
      if (!available) {
        log.warn(
            "document_chunks.embedding column is missing (pgvector not installed?) — "
                + "knowledge search falls back to keyword matching only");
      }
    }
    return available;
  }

  private boolean detectVectorColumn() {
    try {
      Integer count =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM information_schema.columns "
                  + "WHERE table_name = 'document_chunks' AND column_name = 'embedding'",
              Integer.class);
      return count != null && count > 0;
    } catch (Exception e) {
      log.warn("Could not detect vector column: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public String modelName() {
    return modelName;
  }

  @Override
  public float[] embed(String text) {
    float[] result = embedAll(List.of(text)).get(0);
    return result;
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    if (!isEnabled()) {
      IllegalStateException e = new IllegalStateException("Embedding model is not configured");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    List<float[]> vectors = new ArrayList<>(texts.size());
    for (int from = 0; from < texts.size(); from += batchSize) {
      List<String> batch =
          texts.subList(from, Math.min(from + batchSize, texts.size())).stream()
              .map(this::truncate)
              .toList();
      List<float[]> result = embeddingModel.embed(batch);
      if (result.size() != batch.size()) {
        IllegalStateException e =
            new IllegalStateException(
                "Embedding model returned "
                    + result.size()
                    + " vectors for "
                    + batch.size()
                    + " inputs");
        log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
        throw e;
      }
      for (float[] vector : result) {
        if (vector.length != dimensions) {
          IllegalStateException e =
              new IllegalStateException(
                  "Embedding dimension "
                      + vector.length
                      + " does not match expected "
                      + dimensions
                      + " (model: "
                      + modelName
                      + ")");
          log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
          throw e;
        }
        vectors.add(vector);
      }
    }
    return vectors;
  }

  private String truncate(String text) {
    if (text == null) {
      return "";
    }
    return text.length() > maxInputChars ? text.substring(0, maxInputChars) : text;
  }
}
