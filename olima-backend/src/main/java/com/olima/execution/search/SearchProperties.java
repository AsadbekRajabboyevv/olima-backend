package com.olima.execution.search;

import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.search")
public record SearchProperties(
    @DefaultValue({"google", "tavily", "serpapi", "duckduckgo", "wikipedia"})
        List<String> providers,
    @Min(1) @DefaultValue("5") int maxResults,
    @DefaultValue("uz") String language,
    @DefaultValue({"uz", "en"}) List<String> wikipediaLanguages,
    @Min(500) @DefaultValue("6000") int maxPageChars,
    @DefaultValue Key tavily,
    @DefaultValue Key serpapi,
    @DefaultValue Google google) {

  public record Key(@DefaultValue("") String apiKey) {

    public boolean configured() {
      return apiKey != null && !apiKey.isBlank();
    }
  }

  public record Google(@DefaultValue("") String apiKey, @DefaultValue("") String cx) {

    public boolean configured() {
      return apiKey != null && !apiKey.isBlank() && cx != null && !cx.isBlank();
    }
  }
}
