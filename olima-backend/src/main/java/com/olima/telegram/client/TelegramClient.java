package com.olima.telegram.client;

import com.olima.common.http.HttpClientProperties;
import com.olima.telegram.config.TelegramProperties;
import com.olima.telegram.exception.TelegramApiException;
import com.olima.telegram.exception.TelegramPollingException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class TelegramClient {

  // getUpdates javobni pollingTimeout gacha ushlab turadi — umumiy restClient'ning read-timeout'i
  // (20s) bunga yetmaydi, shuning uchun long polling o'z client'ida.
  private static final Duration POLLING_READ_MARGIN = Duration.ofSeconds(15);

  private final RestClient restClient;
  private final RestClient pollingClient;
  private final ObjectMapper objectMapper;
  private final TelegramProperties properties;

  public TelegramClient(
      RestClient restClient,
      ObjectMapper objectMapper,
      TelegramProperties properties,
      HttpClientProperties httpProperties) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.pollingClient = buildPollingClient(httpProperties, properties.pollingTimeout());
  }

  private static RestClient buildPollingClient(HttpClientProperties http, Duration pollTimeout) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(http.connectTimeout()).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(pollTimeout.plus(POLLING_READ_MARGIN));
    return RestClient.builder()
        .requestFactory(factory)
        .defaultHeader("User-Agent", http.userAgent())
        .build();
  }

  public String getBotUsername(String botToken) {
    JsonNode result = call(botToken, "getMe", null);
    JsonNode username = result.path("username");
    if (username.isMissingNode() || username.asString().isBlank()) {
      TelegramApiException e = new TelegramApiException("Telegram did not return a bot username");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    return username.asString();
  }

  public void setWebhook(String botToken, String webhookUrl, String secretToken) {
    call(
        botToken,
        "setWebhook",
        Map.of(
            "url", webhookUrl,
            "secret_token", secretToken,
            "allowed_updates", properties.allowedUpdates()));
  }

  public void deleteWebhook(String botToken) {
    call(botToken, "deleteWebhook", Map.of());
  }

  /**
   * Long polling: offset'dan boshlab yangilanishlarni oladi. Yangi xabar bo'lmasa Telegram javobni
   * pollingTimeout gacha ushlab turadi va bo'sh massiv qaytaradi.
   *
   * @throws TelegramPollingException Telegram ok=false qaytarsa (masalan 409 — webhook o'rnatilgan)
   */
  public JsonNode getUpdates(String botToken, long offset) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("offset", offset);
    body.put("timeout", properties.pollingTimeout().toSeconds());
    body.put("allowed_updates", properties.allowedUpdates());
    String response =
        pollingClient
            .post()
            .uri(methodUrl(botToken, "getUpdates"))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            // 409/401 da ham tanani o'qiymiz — sabab description'da
            .exchange(
                (request, res) -> new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
    JsonNode root = objectMapper.readTree(response);
    if (!root.path("ok").asBoolean(false)) {
      throw new TelegramPollingException(
          root.path("error_code").asInt(0), root.path("description").asString());
    }
    return root.path("result");
  }

  public void sendMessage(String botToken, long chatId, String text) {
    for (String part : split(text, properties.maxMessageLength())) {
      sendPart(botToken, chatId, part);
    }
  }

  private void sendPart(String botToken, long chatId, String text) {
    String parseMode = properties.parseMode();
    if (parseMode != null && !parseMode.isBlank()) {
      try {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", parseMode);
        call(botToken, "sendMessage", body);
        return;
      } catch (Exception e) {
        log.warn(
            "sendMessage with {} failed, retrying as plain text: {}", parseMode, e.getMessage());
      }
    }
    call(botToken, "sendMessage", Map.of("chat_id", chatId, "text", text));
  }

  public static List<String> split(String text, int max) {
    List<String> parts = new ArrayList<>();
    String rest = text == null ? "" : text;
    while (rest.length() > max) {
      int cut = rest.lastIndexOf("\n\n", max);
      if (cut < max / 2) {
        cut = rest.lastIndexOf('\n', max);
      }
      if (cut < max / 2) {
        cut = rest.lastIndexOf(' ', max);
      }
      if (cut < max / 2) {
        cut = max;
      }
      parts.add(rest.substring(0, cut).trim());
      rest = rest.substring(cut).trim();
    }
    if (!rest.isBlank() || parts.isEmpty()) {
      parts.add(rest);
    }
    return parts;
  }

  private String methodUrl(String botToken, String method) {
    return properties.apiBaseUrl().replaceAll("/+$", "") + "/bot" + botToken + "/" + method;
  }

  private JsonNode call(String botToken, String method, Object body) {
    String url = methodUrl(botToken, method);
    try {
      String response =
          (body == null)
              ? restClient.get().uri(url).retrieve().body(String.class)
              : restClient
                  .post()
                  .uri(url)
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(body)
                  .retrieve()
                  .body(String.class);
      JsonNode root = objectMapper.readTree(response);
      if (!root.path("ok").asBoolean(false)) {
        TelegramApiException e =
            new TelegramApiException(
                "Telegram API " + method + " failed: " + root.path("description").asString());
        log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
        throw e;
      }
      return root.path("result");
    } catch (TelegramApiException e) {
      throw e;
    } catch (Exception e) {
      TelegramApiException ex =
          new TelegramApiException("Telegram API " + method + " call failed", e);
      log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
      throw ex;
    }
  }
}
