package com.olima.execution;

import com.olima.common.http.Response;
import com.olima.common.http.SafeHttpClient;
import com.olima.execution.dto.SearchResult;
import com.olima.execution.search.HtmlText;
import com.olima.execution.search.SearchProperties;
import com.olima.execution.search.SearchProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WebSearchServiceImpl implements WebSearchService {

  private final List<SearchProvider> chain;
  private final SafeHttpClient httpClient;
  private final SearchProperties properties;

  public WebSearchServiceImpl(
      List<SearchProvider> providers, SafeHttpClient httpClient, SearchProperties properties) {
    this.httpClient = httpClient;
    this.properties = properties;
    Map<String, SearchProvider> byName =
        providers.stream()
            .collect(Collectors.toMap(p -> p.name().toLowerCase(Locale.ROOT), Function.identity()));
    List<SearchProvider> ordered = new ArrayList<>();
    for (String name : properties.providers()) {
      SearchProvider provider = byName.get(name.trim().toLowerCase(Locale.ROOT));
      if (provider == null) {
        IllegalStateException e =
            new IllegalStateException(
                "Unknown search provider in app.search.providers: "
                    + name
                    + " (available: "
                    + byName.keySet()
                    + ")");
        log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
        throw e;
      }
      ordered.add(provider);
    }
    this.chain = List.copyOf(ordered);
    log.info(
        "Web search providers: {}",
        chain.stream().map(p -> p.name() + (p.isConfigured() ? "" : " (not configured)")).toList());
  }

  @Override
  public List<SearchResult> search(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    for (SearchProvider provider : chain) {
      if (!provider.isConfigured()) {
        continue;
      }
      List<SearchResult> results = provider.search(query, properties.maxResults());
      if (!results.isEmpty()) {
        log.debug("Web search via {} returned {} result(s)", provider.name(), results.size());
        return results;
      }
    }
    return List.of();
  }

  @Override
  public String fetchPage(String url) {
    Response response =
        httpClient.get(
            url,
            Map.of(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language",
                properties.language() + ",ru;q=0.8,en;q=0.7"));
    if (!response.isSuccess()) {
      IllegalStateException e =
          new IllegalStateException("Page returned HTTP " + response.status());
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    String text = HtmlText.toReadableText(response.bodyAsString());
    if (text.isBlank()) {
      IllegalStateException e = new IllegalStateException("Empty page");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    int max = properties.maxPageChars();
    String result =
        text.length() > max ? text.substring(0, max) + "\n... [Content truncated]" : text;
    return result;
  }
}
