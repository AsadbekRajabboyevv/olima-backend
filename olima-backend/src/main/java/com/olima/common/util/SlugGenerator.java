package com.olima.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class SlugGenerator {

  private static final int MAX_LENGTH = 80;
  private static final Map<Character, String> CYRILLIC =
      Map.ofEntries(
          Map.entry('а', "a"),
          Map.entry('б', "b"),
          Map.entry('в', "v"),
          Map.entry('г', "g"),
          Map.entry('д', "d"),
          Map.entry('е', "e"),
          Map.entry('ё', "yo"),
          Map.entry('ж', "j"),
          Map.entry('з', "z"),
          Map.entry('и', "i"),
          Map.entry('й', "y"),
          Map.entry('к', "k"),
          Map.entry('л', "l"),
          Map.entry('м', "m"),
          Map.entry('н', "n"),
          Map.entry('о', "o"),
          Map.entry('п', "p"),
          Map.entry('р', "r"),
          Map.entry('с', "s"),
          Map.entry('т', "t"),
          Map.entry('у', "u"),
          Map.entry('ф', "f"),
          Map.entry('х', "x"),
          Map.entry('ц', "ts"),
          Map.entry('ч', "ch"),
          Map.entry('ш', "sh"),
          Map.entry('щ', "sh"),
          Map.entry('ъ', ""),
          Map.entry('ы', "i"),
          Map.entry('ь', ""),
          Map.entry('э', "e"),
          Map.entry('ю', "yu"),
          Map.entry('я', "ya"),
          Map.entry('ў', "o"),
          Map.entry('қ', "q"),
          Map.entry('ғ', "g"),
          Map.entry('ҳ', "h"));

  private SlugGenerator() {}

  public static String slugify(String input) {
    if (input == null) {
      return "org";
    }
    StringBuilder latin = new StringBuilder();
    for (char c : input.toLowerCase(Locale.ROOT).toCharArray()) {
      String mapped = CYRILLIC.get(c);
      latin.append(mapped != null ? mapped : String.valueOf(c));
    }
    String normalized =
        Normalizer.normalize(latin, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("['ʻʼ’‘`]", "")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
    if (normalized.length() > MAX_LENGTH) {
      normalized = normalized.substring(0, MAX_LENGTH).replaceAll("-+$", "");
    }
    return normalized.isEmpty() ? "org" : normalized;
  }

  public static String unique(String input, Predicate<String> taken) {
    String base = slugify(input);
    String candidate = base;
    for (int i = 2; taken.test(candidate); i++) {
      candidate = base + "-" + i;
    }
    return candidate;
  }
}
