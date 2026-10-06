package com.olima.telegram.util;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TelegramFormatter {

  private static final Pattern MAP_BLOCK =
      Pattern.compile("```(?:map|geo)\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
  private static final Pattern LAT = Pattern.compile("\"lat\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
  private static final Pattern LNG = Pattern.compile("\"lng\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
  private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
  private static final Pattern TABLE_SEPARATOR_ROW =
      Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|?\\s*$");

  public String format(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return markdown;
    }

    String text = MAP_BLOCK.matcher(markdown).replaceAll(this::toMapsLink);
    text = BOLD.matcher(text).replaceAll("*$1*");

    StringBuilder result = new StringBuilder();
    for (String rawLine : text.split("\n", -1)) {
      String line = rawLine;
      if (TABLE_SEPARATOR_ROW.matcher(line).matches()) {
        continue;
      }
      String trimmed = line.trim();
      if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
        line = trimmed.substring(1, trimmed.length() - 1).replace("|", " — ").trim();
      }
      result.append(line).append("\n");
    }

    return result.toString().trim();
  }

  private String toMapsLink(MatchResult match) {
    String json = match.group(1);
    Matcher latMatcher = LAT.matcher(json);
    Matcher lngMatcher = LNG.matcher(json);
    if (latMatcher.find() && lngMatcher.find()) {
      return "📍 https://maps.google.com/?q=" + latMatcher.group(1) + "," + lngMatcher.group(1);
    }
    return "";
  }
}
