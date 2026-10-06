package com.olima.execution.search;

import com.olima.execution.dto.SearchResult;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record JsonProvider(String name, boolean configured, Search search)
    implements SearchProvider {

  @Override
  public boolean isConfigured() {
    return configured;
  }

  @Override
  public List<SearchResult> search(String query, int maxResults) {
    try {
      return search.run(query, maxResults);
    } catch (Exception e) {
      log.warn("{} search failed: {}", name, e.getMessage());
      return List.of();
    }
  }
}
