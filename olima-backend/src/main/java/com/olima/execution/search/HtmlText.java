package com.olima.execution.search;

public final class HtmlText {

  private HtmlText() {}

  public static String clean(String text) {
    if (text == null) {
      return "";
    }
    return decodeEntities(text.replaceAll("<[^>]*>", "")).trim();
  }

  public static String toReadableText(String html) {
    String text =
        html.replaceAll("(?is)<script.*?</script>", " ")
            .replaceAll("(?is)<style.*?</style>", " ")
            .replaceAll("(?is)<svg.*?</svg>", " ")
            .replaceAll("(?is)<header.*?</header>", " ")
            .replaceAll("(?is)<footer.*?</footer>", " ")
            .replaceAll("(?is)<nav.*?</nav>", " ")
            .replaceAll("(?i)<tr[^>]*>", "\n")
            .replaceAll("(?i)<t[dh][^>]*>", " | ")
            .replaceAll("(?i)</t[rdh]>", " |")
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</p>", "\n\n")
            .replaceAll("<[^>]*>", " ");
    return decodeEntities(text).replaceAll("[ \t]+", " ").replaceAll("\n{3,}", "\n\n").trim();
  }

  private static String decodeEntities(String text) {
    return text.replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&");
  }
}
