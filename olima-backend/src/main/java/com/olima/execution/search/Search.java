package com.olima.execution.search;

import com.olima.execution.dto.SearchResult;
import java.util.List;

@FunctionalInterface
public interface Search {

  List<SearchResult> run(String query, int maxResults) throws Exception;
}
