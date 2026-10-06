package com.olima.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.olima.organization.OrganizationEntity;
import com.olima.organization.OrganizationMapper;
import com.olima.organization.OrganizationRepository;
import com.olima.tool.ToolEntity;
import com.olima.tool.ToolRepository;
import com.olima.tool.enums.AccessLevel;
import com.olima.tool.enums.ToolType;
import com.olima.user.UserEntity;
import com.olima.user.UserRepository;
import com.olima.user.enums.UserRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class PlatformSecurityIntegrationTest {

  private static final String PASSWORD = "Integration123";

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Value("${local.server.port}")
  int port;

  @Autowired OrganizationRepository organizations;
  @Autowired UserRepository users;
  @Autowired ToolRepository tools;
  @Autowired PasswordEncoder passwordEncoder;

  RestClient http;
  OrganizationEntity orgA;
  OrganizationEntity orgB;
  ToolEntity toolOfB;

  @BeforeEach
  void setUp() {
    http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {})
            .build();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    orgA =
        organizations.save(
            OrganizationEntity.builder()
                .name("A " + suffix)
                .slug("a-" + suffix)
                .enabled(true)
                .widgetKey(OrganizationMapper.newWidgetKey())
                .build());
    orgB =
        organizations.save(
            OrganizationEntity.builder()
                .name("B " + suffix)
                .slug("b-" + suffix)
                .enabled(true)
                .widgetKey(OrganizationMapper.newWidgetKey())
                .build());
    users.save(
        UserEntity.builder()
            .username("admin-a-" + suffix)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .role(UserRole.ORG_ADMIN)
            .organizationId(orgA.getId())
            .enabled(true)
            .build());
    toolOfB =
        tools.save(
            ToolEntity.builder()
                .organizationId(orgB.getId())
                .name("tool_" + suffix)
                .description("x")
                .type(ToolType.RAG)
                .enabled(true)
                .accessLevel(AccessLevel.READ)
                .build());
  }

  private String loginAsAdminOfA() {
    String username =
        users.findAll().stream()
            .filter(u -> orgA.getId().equals(u.getOrganizationId()))
            .findFirst()
            .orElseThrow()
            .getUsername();
    ResponseEntity<Map> response =
        http.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("username", username, "password", PASSWORD))
            .retrieve()
            .toEntity(Map.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
        .anySatisfy(c -> assertThat(c).contains("HttpOnly"));
    return (String) response.getBody().get("token");
  }

  @Test
  void orgAdminCannotReadAnotherOrganizationsResources() {
    String token = loginAsAdminOfA();

    assertThat(
            http.get()
                .uri("/api/v1/tools/{id}", toolOfB.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value())
        .isEqualTo(403);
    assertThat(
            http.get()
                .uri("/api/v1/conversations?organizationId={id}", orgB.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value())
        .isEqualTo(403);
    assertThat(
            http.get()
                .uri("/api/v1/executions?conversationId={id}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value())
        .isEqualTo(403);
    assertThat(
            http.get()
                .uri("/api/v1/organizations/{id}", orgA.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value())
        .isEqualTo(200);
  }

  @Test
  void knowledgeBaseIsCreatedInOwnOrganizationRegardlessOfBody() {
    String token = loginAsAdminOfA();
    ResponseEntity<Map> response =
        http.post()
            .uri("/api/v1/knowledge/bases")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "id",
                    UUID.randomUUID().toString(),
                    "name",
                    "KB",
                    "organizationId",
                    orgB.getId().toString()))
            .retrieve()
            .toEntity(Map.class);
    assertThat(response.getStatusCode().value()).isEqualTo(403);

    ResponseEntity<Map> own =
        http.post()
            .uri("/api/v1/knowledge/bases")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", "KB"))
            .retrieve()
            .toEntity(Map.class);
    assertThat(own.getStatusCode().value()).isEqualTo(201);
    assertThat(own.getBody().get("organizationId")).isEqualTo(orgA.getId().toString());
  }

  @Test
  void toolPointingToInternalNetworkIsRejected() {
    String token = loginAsAdminOfA();
    ResponseEntity<Map> response =
        http.post()
            .uri("/api/v1/tools?organizationId={id}", orgA.getId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "name",
                    "metadata",
                    "description",
                    "x",
                    "type",
                    "REST_API",
                    "configuration",
                    "{\"endpoint\":\"http://169.254.169.254/latest/meta-data\"}",
                    "parameters",
                    List.of()))
            .retrieve()
            .toEntity(Map.class);
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody().get("code")).isEqualTo("OUTBOUND_REQUEST_REJECTED");
  }

  @Test
  void widgetKeyCannotReachPanelEndpoints() {
    assertThat(
            http.get()
                .uri("/api/v1/organizations/{id}", orgA.getId())
                .header("X-Widget-Key", orgA.getWidgetKey())
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value())
        .isEqualTo(401);
    assertThat(
            http.get()
                .uri("/api/v1/chat/widget-config")
                .header("X-Widget-Key", orgA.getWidgetKey())
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value())
        .isEqualTo(200);
  }

  @Test
  void refreshRotatesAndOldRefreshTokenIsRejected() {
    loginAsAdminOfA();
    String username =
        users.findAll().stream()
            .filter(u -> orgA.getId().equals(u.getOrganizationId()))
            .findFirst()
            .orElseThrow()
            .getUsername();
    ResponseEntity<Map> login =
        http.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("username", username, "password", PASSWORD))
            .retrieve()
            .toEntity(Map.class);
    String cookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";")[0];

    ResponseEntity<Map> refreshed =
        http.post()
            .uri("/api/v1/auth/refresh")
            .header(HttpHeaders.COOKIE, cookie)
            .retrieve()
            .toEntity(Map.class);
    assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
    assertThat(refreshed.getBody().get("token")).isNotNull();

    assertThat(
            http.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.COOKIE, cookie)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value())
        .isEqualTo(401);
  }

  @Test
  void problemDetailIsReturnedForUnauthenticatedRequests() {
    ResponseEntity<Map> response =
        http.get().uri("/api/v1/organizations").retrieve().toEntity(Map.class);
    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(response.getBody()).containsEntry("code", "UNAUTHORIZED");
  }
}
