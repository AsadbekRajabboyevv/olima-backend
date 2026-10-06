package com.olima.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.olima.common.error.BusinessException;
import com.olima.common.http.OutboundRequestException;
import com.olima.common.http.OutboundUrlPolicy;
import com.olima.execution.config.RestApiToolProperties;
import com.olima.execution.dto.ToolResult;
import com.olima.execution.executor.ToolExecutor;
import com.olima.execution.executor.ToolExecutorRegistry;
import com.olima.integration.IntegrationConnectionService;
import com.olima.tool.dto.ToolParameterRequest;
import com.olima.tool.dto.ToolRequest;
import com.olima.tool.enums.AccessLevel;
import com.olima.tool.enums.ToolType;
import com.olima.tool.util.ToolNames;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;

class ToolServiceImplTest {

  private final UUID orgId = UUID.randomUUID();
  private final UUID toolId = UUID.randomUUID();

  private ToolRepository repository;
  private OutboundUrlPolicy urlPolicy;
  private IntegrationConnectionService connectionService;
  private ToolServiceImpl service;
  private ToolEntity tool;

  @BeforeEach
  void setUp() {
    repository = mock(ToolRepository.class);
    urlPolicy = mock(OutboundUrlPolicy.class);
    connectionService = mock(IntegrationConnectionService.class);
    when(connectionService.exists(orgId, "hemis")).thenReturn(true);
    ToolExecutorRegistry executors =
        new ToolExecutorRegistry(
            List.of(stubExecutor(ToolType.REST_API), stubExecutor(ToolType.RAG)));
    RestApiToolProperties restProps =
        new RestApiToolProperties(
            Duration.ofSeconds(15),
            Duration.ofSeconds(30),
            DataSize.ofKilobytes(512),
            16000,
            List.of("GET", "POST", "PUT", "PATCH", "DELETE"),
            List.of("host", "content-length"));
    service =
        new ToolServiceImpl(
            repository,
            new ToolMapper(),
            new ObjectMapper(),
            executors,
            restProps,
            urlPolicy,
            connectionService);

    tool =
        ToolEntity.builder()
            .organizationId(orgId)
            .name("get_student_profile")
            .description("Student profile")
            .type(ToolType.REST_API)
            .configuration("{\"endpoint\":\"http://api/students/{studentId}\",\"method\":\"GET\"}")
            .enabled(true)
            .accessLevel(AccessLevel.READ)
            .parameters(new ArrayList<>())
            .build();
    tool.setId(toolId);
    ToolParameterEntity studentId =
        ToolParameterEntity.builder()
            .tool(tool)
            .name("studentId")
            .type("string")
            .description("old")
            .required(true)
            .build();
    ToolParameterEntity lang =
        ToolParameterEntity.builder()
            .tool(tool)
            .name("lang")
            .type("string")
            .required(false)
            .build();
    tool.getParameters().addAll(List.of(studentId, lang));

    when(repository.findById(toolId)).thenReturn(Optional.of(tool));
    when(repository.save(any(ToolEntity.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private static ToolExecutor stubExecutor(ToolType type) {
    return new ToolExecutor() {
      @Override
      public ToolResult execute(ToolEntity tool, Map<String, Object> parameters) {
        return ToolResult.success(null);
      }

      @Override
      public ToolType supportedType() {
        return type;
      }
    };
  }

  private ToolRequest request(
      String name, String config, Boolean enabled, List<ToolParameterRequest> params) {
    return new ToolRequest(
        name,
        "Student profile",
        ToolType.REST_API,
        config,
        false,
        AccessLevel.READ,
        enabled,
        params);
  }

  @Test
  void updateKeepsExistingParameterEntitiesInsteadOfReinsertingThem() {
    ToolParameterEntity original = tool.getParameters().get(0);

    service.update(
        toolId,
        request(
            "get_student_profile",
            tool.getConfiguration(),
            null,
            List.of(
                new ToolParameterRequest("studentId", "string", "new description", true, null),
                new ToolParameterRequest("year", "number", null, false, null))));

    assertThat(tool.getParameters())
        .extracting(ToolParameterEntity::getName)
        .containsExactlyInAnyOrder("studentId", "year");

    assertThat(tool.getParameters()).contains(original);
    assertThat(original.getDescription()).isEqualTo("new description");
  }

  @Test
  void updateSavesEnabledFlagOnlyWhenProvided() {
    service.update(
        toolId, request("get_student_profile", tool.getConfiguration(), false, studentIdOnly()));
    assertThat(tool.isEnabled()).isFalse();

    service.update(
        toolId, request("get_student_profile", tool.getConfiguration(), null, studentIdOnly()));
    assertThat(tool.isEnabled()).isFalse();
  }

  @Test
  void placeholderWithoutDeclaredParameterIsRejected() {
    assertThatThrownBy(
            () ->
                service.update(
                    toolId,
                    request("get_student_profile", tool.getConfiguration(), null, List.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("studentId");
  }

  @Test
  void placeholderInHostIsRejected() {
    assertThatThrownBy(
            () ->
                service.update(
                    toolId,
                    request(
                        "get_student_profile",
                        "{\"endpoint\":\"https://{host}/x\"}",
                        null,
                        List.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void internalEndpointIsRejectedBySsrfPolicy() {
    doThrow(new OutboundRequestException("Internal/private network addresses are not allowed"))
        .when(urlPolicy)
        .requireAllowed(anyString());
    assertThatThrownBy(
            () ->
                service.update(
                    toolId,
                    request(
                        "get_student_profile",
                        "{\"endpoint\":\"http://169.254.169.254/latest/{studentId}\"}",
                        null,
                        studentIdOnly())))
        .isInstanceOf(OutboundRequestException.class);
  }

  @Test
  void unsupportedTypeIsRejected() {
    ToolRequest workflow =
        new ToolRequest(
            "wf", "Workflow", ToolType.WORKFLOW, "{}", false, AccessLevel.READ, null, List.of());
    assertThatThrownBy(() -> service.create(orgId, workflow)).isInstanceOf(BusinessException.class);
  }

  @Test
  void unknownParameterTypeIsRejected() {
    assertThatThrownBy(
            () ->
                service.update(
                    toolId,
                    request(
                        "get_student_profile",
                        tool.getConfiguration(),
                        null,
                        List.of(
                            new ToolParameterRequest("studentId", "object", null, true, null)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("object");
  }

  @Test
  void relativeEndpointRequiresExistingConnection() {
    service.update(
        toolId,
        request(
            "get_student_profile",
            "{\"connection\":\"hemis\",\"endpoint\":\"/students/{studentId}\"}",
            null,
            studentIdOnly()));
    assertThat(tool.getConfiguration()).contains("hemis");

    assertThatThrownBy(
            () ->
                service.update(
                    toolId,
                    request(
                        "get_student_profile",
                        "{\"connection\":\"unknown\",\"endpoint\":\"/students/{studentId}\"}",
                        null,
                        studentIdOnly())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown");
    assertThatThrownBy(
            () ->
                service.update(
                    toolId,
                    request(
                        "get_student_profile",
                        "{\"endpoint\":\"/students/{studentId}\"}",
                        null,
                        studentIdOnly())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static List<ToolParameterRequest> studentIdOnly() {
    return List.of(new ToolParameterRequest("studentId", "string", null, true, null));
  }

  @Test
  void duplicateParameterNamesAreRejected() {
    assertThatThrownBy(
            () ->
                service.update(
                    toolId,
                    request(
                        "get_student_profile",
                        tool.getConfiguration(),
                        null,
                        List.of(
                            new ToolParameterRequest("studentId", "string", null, true, null),
                            new ToolParameterRequest("studentId", "number", null, true, null)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("studentId");
  }

  @Test
  void invalidJsonAndMissingEndpointAreRejected() {
    assertThatThrownBy(
            () ->
                service.update(
                    toolId, request("get_student_profile", "{not json", null, List.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.update(
                    toolId,
                    request("get_student_profile", "{\"method\":\"GET\"}", null, List.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("endpoint");
  }

  @Test
  void reservedBuiltInNamesAreRejected() {
    assertThatThrownBy(
            () ->
                service.create(
                    orgId,
                    request(
                        ToolNames.SEARCH_KNOWLEDGE_BASE, tool.getConfiguration(), null, List.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
