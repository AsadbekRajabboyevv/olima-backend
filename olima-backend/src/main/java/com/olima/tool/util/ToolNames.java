package com.olima.tool.util;

import java.util.Set;
import java.util.regex.Pattern;

public final class ToolNames {

  public static final String SEARCH_KNOWLEDGE_BASE = "search_knowledge_base";
  public static final String SEARCH_INTERNET = "search_internet";
  public static final String FETCH_WEB_PAGE = "fetch_web_page";

  private static final Pattern VALID = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
  private static final Set<String> RESERVED =
      Set.of(SEARCH_KNOWLEDGE_BASE, SEARCH_INTERNET, FETCH_WEB_PAGE);

  private ToolNames() {}

  public static boolean isValid(String name) {
    return name != null && VALID.matcher(name).matches();
  }

  public static boolean isReserved(String name) {
    return name != null && RESERVED.contains(name);
  }
}
