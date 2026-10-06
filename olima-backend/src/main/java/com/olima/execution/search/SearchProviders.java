package com.olima.execution.search;

import com.olima.execution.dto.SearchResult;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Configuration
public class SearchProviders {

  @Bean
  SearchProvider googleSearchProvider(
      RestClient restClient, ObjectMapper mapper, SearchProperties props) {
    return new JsonProvider(
        "google",
        props.google().configured(),
        (query, max) -> {
          String url =
              UriComponentsBuilder.fromUriString("https://www.googleapis.com/customsearch/v1")
                  .queryParam("q", query)
                  .queryParam("key", props.google().apiKey())
                  .queryParam("cx", props.google().cx())
                  .queryParam("num", Math.min(max, 10))
                  .encode()
                  .build()
                  .toUriString();
          JsonNode items =
              mapper
                  .readTree(restClient.get().uri(url).retrieve().body(String.class))
                  .path("items");
          return collect(items, max, "title", "snippet", "link");
        });
  }

  @Bean
  SearchProvider tavilySearchProvider(
      RestClient restClient, ObjectMapper mapper, SearchProperties props) {
    return new JsonProvider(
        "tavily",
        props.tavily().configured(),
        (query, max) -> {
          String response =
              restClient
                  .post()
                  .uri("https://api.tavily.com/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(
                      Map.of(
                          "api_key",
                          props.tavily().apiKey(),
                          "query",
                          query,
                          "search_depth",
                          "advanced",
                          "include_answer",
                          false,
                          "max_results",
                          max))
                  .retrieve()
                  .body(String.class);
          return collect(mapper.readTree(response).path("results"), max, "title", "content", "url");
        });
  }

  @Bean
  SearchProvider serpApiSearchProvider(
      RestClient restClient, ObjectMapper mapper, SearchProperties props) {
    return new JsonProvider(
        "serpapi",
        props.serpapi().configured(),
        (query, max) -> {
          String url =
              UriComponentsBuilder.fromUriString("https://serpapi.com/search.json")
                  .queryParam("engine", "google")
                  .queryParam("q", query)
                  .queryParam("api_key", props.serpapi().apiKey())
                  .queryParam("hl", props.language())
                  .queryParam("gl", props.language())
                  .encode()
                  .build()
                  .toUriString();
          JsonNode organic =
              mapper
                  .readTree(restClient.get().uri(url).retrieve().body(String.class))
                  .path("organic_results");
          return collect(organic, max, "title", "snippet", "link");
        });
  }

  @Bean
  SearchProvider duckDuckGoSearchProvider(RestClient restClient, SearchProperties props) {
    Pattern titlePattern =
        Pattern.compile(
            "<h2[^>]+class=\"result__title\"[^>]*>\\s*<a[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    Pattern snippetPattern =
        Pattern.compile(
            "<a[^>]+class=\"result__snippet\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    return new JsonProvider(
        "duckduckgo",
        true,
        (query, max) -> {
          String url =
              UriComponentsBuilder.fromUriString("https://html.duckduckgo.com/html/")
                  .queryParam("q", query)
                  .encode()
                  .build()
                  .toUriString();
          String html =
              restClient
                  .get()
                  .uri(url)
                  .header("Accept", "text/html,application/xhtml+xml")
                  .header("Accept-Language", props.language() + ",ru;q=0.8,en;q=0.7")
                  .retrieve()
                  .body(String.class);
          List<SearchResult> results = new ArrayList<>();
          if (html == null || html.isBlank()) {
            return results;
          }
          List<String> titles = new ArrayList<>();
          Matcher titleMatcher = titlePattern.matcher(html);
          while (titleMatcher.find()) {
            titles.add(HtmlText.clean(titleMatcher.group(1)));
          }
          Matcher snippetMatcher = snippetPattern.matcher(html);
          int idx = 0;
          while (snippetMatcher.find() && results.size() < max) {
            String snippet = HtmlText.clean(snippetMatcher.group(2));
            String title = idx < titles.size() ? titles.get(idx) : "Web Result";
            idx++;
            if (!snippet.isBlank()) {
              results.add(
                  new SearchResult(title, snippet, extractTargetUrl(snippetMatcher.group(1))));
            }
          }
          return results;
        });
  }

  @Bean
  SearchProvider wikipediaSearchProvider(
      RestClient restClient, ObjectMapper mapper, SearchProperties props) {
    return new JsonProvider(
        "wikipedia",
        true,
        (query, max) -> {
          for (String lang : props.wikipediaLanguages()) {
            String url =
                UriComponentsBuilder.fromUriString("https://" + lang + ".wikipedia.org/w/api.php")
                    .queryParam("action", "query")
                    .queryParam("list", "search")
                    .queryParam("srsearch", query)
                    .queryParam("format", "json")
                    .queryParam("utf8", 1)
                    .encode()
                    .build()
                    .toUriString();
            JsonNode nodes =
                mapper
                    .readTree(restClient.get().uri(url).retrieve().body(String.class))
                    .path("query")
                    .path("search");
            List<SearchResult> results = new ArrayList<>();
            for (JsonNode node : nodes) {
              String title = node.path("title").asString();
              results.add(
                  new SearchResult(
                      title,
                      HtmlText.clean(node.path("snippet").asString()),
                      "https://"
                          + lang
                          + ".wikipedia.org/wiki/"
                          + UriComponentsBuilder.newInstance()
                              .pathSegment(title.replace(' ', '_'))
                              .encode()
                              .build()
                              .toUriString()
                              .substring(1)));
              if (results.size() >= max) {
                break;
              }
            }
            if (!results.isEmpty()) {
              return results;
            }
          }
          return List.of();
        });
  }

  private static List<SearchResult> collect(
      JsonNode items, int max, String titleField, String snippetField, String urlField) {
    List<SearchResult> results = new ArrayList<>();
    if (items != null && items.isArray()) {
      for (JsonNode item : items) {
        results.add(
            new SearchResult(
                item.path(titleField).asString(),
                item.path(snippetField).asString(),
                item.path(urlField).asString()));
        if (results.size() >= max) {
          break;
        }
      }
    }
    return results;
  }

  private static String extractTargetUrl(String rawHref) {
    try {
      if (rawHref.contains("uddg=")) {
        String sub = rawHref.substring(rawHref.indexOf("uddg=") + 5);
        int amp = sub.indexOf('&');
        return URLDecoder.decode(amp != -1 ? sub.substring(0, amp) : sub, StandardCharsets.UTF_8);
      }
      return rawHref.startsWith("//") ? "https:" + rawHref : rawHref;
    } catch (Exception e) {
      return rawHref;
    }
  }
}
