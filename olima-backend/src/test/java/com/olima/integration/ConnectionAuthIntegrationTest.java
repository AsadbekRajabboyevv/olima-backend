package com.olima.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.olima.common.http.HttpClientProperties;
import com.olima.common.http.Outbound;
import com.olima.common.http.OutboundRequestException;
import com.olima.common.http.OutboundUrlPolicy;
import com.olima.common.http.SafeHttpClient;
import com.olima.execution.config.RestApiToolProperties;
import com.olima.execution.dto.ToolResult;
import com.olima.execution.executor.RestApiToolExecutor;
import com.olima.integration.auth.ConnectionAuthenticator;
import com.olima.integration.config.AuthSettings;
import com.olima.integration.config.IntegrationProperties;
import com.olima.integration.dto.ConnectionSnapshot;
import com.olima.integration.enums.AuthType;
import com.olima.tool.ToolEntity;
import com.olima.tool.ToolParameterEntity;
import com.olima.tool.enums.ToolType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ConnectionAuthIntegrationTest {

  private static final String USER = "olima-bot";
  private static final String PASS = "S3cret-pass";

  private final ObjectMapper mapper = new ObjectMapper();
  private final UUID orgId = UUID.randomUUID();
  private final AtomicInteger logins = new AtomicInteger();
  private final AtomicReference<String> validToken = new AtomicReference<>();
  private final List<String> receivedBodies = new ArrayList<>();

  private HttpServer server;
  private ConnectionAuthenticator authenticator;
  private IntegrationConnectionService connections;
  private RestApiToolExecutor executor;
  private URI base;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/auth/login", this::login);
    server.createContext("/students/", this::student);
    server.start();
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

    HttpClientProperties http =
        new HttpClientProperties(
            Duration.ofSeconds(2),
            Duration.ofSeconds(5),
            DataSize.ofKilobytes(256),
            3,
            "test",
            new Outbound(true, List.of(), List.of(), List.of("http", "https")));
    SafeHttpClient client = new SafeHttpClient(http, new OutboundUrlPolicy(http));
    authenticator =
        new ConnectionAuthenticator(
            client,
            mapper,
            new IntegrationProperties(
                Duration.ofMinutes(30), Duration.ofSeconds(60), Duration.ofSeconds(5), 100),
            new SimpleMeterRegistry());
    connections = mock(IntegrationConnectionService.class);
    executor =
        new RestApiToolExecutor(
            client,
            mapper,
            new RestApiToolProperties(
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                DataSize.ofKilobytes(64),
                16000,
                List.of("GET", "POST"),
                List.of("host")),
            connections,
            authenticator);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private ConnectionSnapshot connection(String password) {
    AuthSettings settings =
        new AuthSettings(
                "/api/auth/login",
                null,
                "login",
                "parol",
                null,
                "data.accessToken",
                "data.expiresIn",
                null,
                null,
                null,
                null)
            .withDefaults(AuthType.LOGIN, 1800);
    ConnectionSnapshot snapshot =
        new ConnectionSnapshot(
            UUID.randomUUID(),
            orgId,
            "hemis",
            base,
            AuthType.LOGIN,
            settings,
            USER,
            password,
            null);
    when(connections.snapshot(orgId, "hemis")).thenReturn(snapshot);
    return snapshot;
  }

  private ToolEntity tool(String endpoint) {
    ToolEntity tool =
        ToolEntity.builder()
            .organizationId(orgId)
            .name("get_student")
            .type(ToolType.REST_API)
            .configuration("{\"connection\":\"hemis\",\"endpoint\":\"" + endpoint + "\"}")
            .parameters(new ArrayList<>())
            .build();
    tool.getParameters()
        .add(
            ToolParameterEntity.builder()
                .tool(tool)
                .name("id")
                .type("string")
                .required(true)
                .build());
    return tool;
  }

  @Test
  void tokenIsObtainedOnceAndReused() {
    connection(PASS);
    ToolEntity tool = tool("/students/{id}");

    ToolResult first = executor.execute(tool, Map.of("id", "42"));
    ToolResult second = executor.execute(tool, Map.of("id", "43"));

    assertThat(first.success()).isTrue();
    assertThat(first.data().toString()).contains("\"id\":\"42\"");
    assertThat(second.success()).isTrue();
    assertThat(logins.get()).isEqualTo(1);

    assertThat(receivedBodies.getFirst()).contains("\"login\":\"olima-bot\"");
  }

  @Test
  void revokedTokenTriggersSingleReLogin() {
    connection(PASS);
    ToolEntity tool = tool("/students/{id}");
    assertThat(executor.execute(tool, Map.of("id", "1")).success()).isTrue();

    validToken.set("revoked-by-server");
    ToolResult result = executor.execute(tool, Map.of("id", "2"));

    assertThat(result.success()).isTrue();
    assertThat(logins.get()).isEqualTo(2);
  }

  @Test
  void wrongPasswordGivesGenericErrorWithoutSecrets() {
    connection("wrong-password");
    ToolResult result = executor.execute(tool("/students/{id}"), Map.of("id", "1"));

    assertThat(result.success()).isFalse();
    assertThat(result.error())
        .isEqualTo("Authentication to the external system failed")
        .doesNotContain("wrong-password")
        .doesNotContain(USER);
  }

  @Test
  void credentialsAreNeverSentToAnotherHost() {
    ConnectionSnapshot snapshot = connection(PASS);
    assertThatThrownBy(() -> authenticator.resolve(snapshot, "http://evil.example/steal"))
        .isInstanceOf(OutboundRequestException.class);

    ToolResult result =
        executor.execute(tool("http://127.0.0.2:1/students/{id}"), Map.of("id", "1"));
    assertThat(result.success()).isFalse();
    assertThat(logins.get()).isZero();
  }

  @Test
  void pathParameterCannotEscapeTheBasePath() {
    connection(PASS);
    ToolResult result = executor.execute(tool("/students/{id}"), Map.of("id", "../admin"));
    assertThat(result.success()).isTrue();

    assertThat(result.data().toString()).contains("..%2Fadmin");
  }

  @Test
  void jsonPathPointer() {
    assertThat(ConnectionAuthenticator.pointer("data.accessToken")).isEqualTo("/data/accessToken");
    assertThat(ConnectionAuthenticator.pointer("items.0.token")).isEqualTo("/items/0/token");
    assertThat(ConnectionAuthenticator.pointer("/raw/pointer")).isEqualTo("/raw/pointer");
  }

  private void login(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    receivedBodies.add(body);
    JsonNode json = mapper.readTree(body);
    if (!USER.equals(json.path("login").asString())
        || !PASS.equals(json.path("parol").asString())) {
      respond(exchange, 401, "{\"error\":\"bad credentials\"}");
      return;
    }
    String token = "tok-" + logins.incrementAndGet();
    validToken.set(token);
    respond(exchange, 200, "{\"data\":{\"accessToken\":\"" + token + "\",\"expiresIn\":3600}}");
  }

  private void student(HttpExchange exchange) throws IOException {
    String auth = exchange.getRequestHeaders().getFirst("Authorization");
    if (auth == null || !auth.equals("Bearer " + validToken.get())) {
      respond(exchange, 401, "{\"error\":\"unauthorized\"}");
      return;
    }
    String raw = exchange.getRequestURI().getRawPath().substring("/students/".length());
    respond(exchange, 200, "{\"id\":\"" + raw + "\",\"name\":\"Ali\"}");
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
