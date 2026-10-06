package com.olima.execution;

import com.olima.execution.dto.SearchResult;
import java.util.List;

public interface WebSearchService {

  List<SearchResult> search(String query);

  String fetchPage(String url);
}
