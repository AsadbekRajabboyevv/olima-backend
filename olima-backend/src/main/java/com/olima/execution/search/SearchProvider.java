package com.olima.execution.search;

import com.olima.execution.dto.SearchResult;
import java.util.List;

public interface SearchProvider {

  String name();

  boolean isConfigured();

  List<SearchResult> search(String query, int maxResults);
}
