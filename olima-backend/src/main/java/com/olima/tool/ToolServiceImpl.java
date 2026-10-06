package com.olima.tool;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.common.error.NotFoundException;
import com.olima.common.http.OutboundUrlPolicy;
import com.olima.execution.config.RestApiToolProperties;
import com.olima.execution.config.RestToolConfig;
import com.olima.execution.executor.ToolExecutorRegistry;
import com.olima.integration.IntegrationConnectionService;
import com.olima.tool.dto.ToolParameterRequest;
import com.olima.tool.dto.ToolRequest;
import com.olima.tool.dto.ToolResponse;
import com.olima.tool.enums.AccessLevel;
import com.olima.tool.enums.ToolType;
import com.olima.tool.util.ToolNames;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolServiceImpl implements ToolService {

  public static final String ENABLED_TOOLS_CACHE = "enabledTools";
  private static final Set<String> PARAMETER_TYPES =
      Set.of("string", "number", "integer", "boolean");

  private final ToolRepository toolRepository;
  private final ToolMapper toolMapper;
  private final ObjectMapper objectMapper;
  private final ToolExecutorRegistry executorRegistry;
  private final RestApiToolProperties restApiProperties;
  private final OutboundUrlPolicy outboundUrlPolicy;
  private final IntegrationConnectionService connectionService;

  @Override
  public List<ToolType> getSupportedTypes() {
    List<ToolType> list = executorRegistry.supportedTypes().stream().sorted().toList();
    return list;
  }

  @Override
  public List<ToolResponse> findByOrganization(UUID orgId) {
    List<ToolResponse> list =
        toolRepository.findByOrganizationId(orgId).stream()
            .map(toolMapper::toResponse)
            .collect(Collectors.toList());
    return list;
  }

  @Override
  public ToolResponse findById(UUID id) {
    ToolResponse response =
        toolRepository
            .findById(id)
            .map(toolMapper::toResponse)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("get.tool_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    return response;
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = ENABLED_TOOLS_CACHE, allEntries = true)
  public ToolResponse create(UUID orgId, ToolRequest request) {
    validate(orgId, request);
    if (toolRepository.findByOrganizationIdAndName(orgId, request.name()).isPresent()) {
      BusinessException e =
          new BusinessException(
              ErrorCode.DUPLICATE_NAME,
              "Bu tashkilotda '" + request.name() + "' nomli vosita allaqachon bor");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    ToolEntity tool = toolMapper.toEntity(request);
    tool.setConfiguration(normalizeConfiguration(request.configuration()));
    tool.setOrganizationId(orgId);
    ToolResponse response = toolMapper.toResponse(toolRepository.save(tool));
    return response;
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = ENABLED_TOOLS_CACHE, allEntries = true)
  public ToolResponse update(UUID id, ToolRequest request) {
    ToolEntity tool =
        toolRepository
            .findById(id)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("update.tool_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    validate(tool.getOrganizationId(), request);

    if (!tool.getName().equals(request.name())
        && toolRepository
            .findByOrganizationIdAndName(tool.getOrganizationId(), request.name())
            .isPresent()) {
      BusinessException e =
          new BusinessException(
              ErrorCode.DUPLICATE_NAME,
              "Bu tashkilotda '" + request.name() + "' nomli vosita allaqachon bor");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }

    tool.setName(request.name());
    tool.setDescription(request.description());
    tool.setType(request.type());
    tool.setConfiguration(normalizeConfiguration(request.configuration()));
    tool.setRequiresConfirmation(Boolean.TRUE.equals(request.requiresConfirmation()));
    tool.setAccessLevel(request.accessLevel() != null ? request.accessLevel() : AccessLevel.READ);
    if (request.enabled() != null) {
      tool.setEnabled(request.enabled());
    }
    mergeParameters(tool, request.parameters());

    ToolResponse response = toolMapper.toResponse(toolRepository.save(tool));
    return response;
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = ENABLED_TOOLS_CACHE, allEntries = true)
  public void delete(UUID id) {
    if (!toolRepository.existsById(id)) {
      NotFoundException e = new NotFoundException("delete.tool_not_found");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }

    toolRepository.deleteById(id);
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = ENABLED_TOOLS_CACHE, allEntries = true)
  public ToolResponse toggleEnabled(UUID id, boolean enabled) {
    ToolEntity tool =
        toolRepository
            .findById(id)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("toggle.tool_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    tool.setEnabled(enabled);
    ToolResponse response = toolMapper.toResponse(toolRepository.save(tool));
    return response;
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = ENABLED_TOOLS_CACHE, allEntries = true)
  public ToolResponse toggleEnabled(UUID id, Boolean enabled, Map<String, Object> body) {
    boolean isEnabled =
        enabled != null
            ? enabled
            : body == null
                || !body.containsKey("enabled")
                || Boolean.parseBoolean(String.valueOf(body.get("enabled")));
    return toggleEnabled(id, isEnabled);
  }

  private void mergeParameters(ToolEntity tool, List<ToolParameterRequest> requested) {
    List<ToolParameterRequest> incoming = requested != null ? requested : List.of();
    Map<String, ToolParameterRequest> byName =
        incoming.stream()
            .collect(
                Collectors.toMap(
                    ToolParameterRequest::name,
                    Function.identity(),
                    (a, b) -> a,
                    LinkedHashMap::new));

    tool.getParameters().removeIf(existing -> !byName.containsKey(existing.getName()));

    Map<String, ToolParameterEntity> existingByName =
        tool.getParameters().stream()
            .collect(Collectors.toMap(ToolParameterEntity::getName, Function.identity()));
    for (ToolParameterRequest p : byName.values()) {
      ToolParameterEntity entity = existingByName.get(p.name());
      if (entity == null) {
        tool.getParameters().add(toolMapper.toParameterEntity(p, tool));
      } else {
        entity.setType(p.type());
        entity.setDescription(p.description());
        entity.setRequired(Boolean.TRUE.equals(p.required()));
        entity.setDefaultValue(p.defaultValue());
      }
    }
  }

  private void validate(UUID orgId, ToolRequest request) {
    if (!executorRegistry.supports(request.type())) {
      BusinessException e =
          new BusinessException(
              ErrorCode.UNSUPPORTED_TOOL_TYPE,
              "Bu vosita turi hozircha qo'llab-quvvatlanmaydi: " + request.type());
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    if (ToolNames.isReserved(request.name())) {
      IllegalArgumentException e =
          new IllegalArgumentException(
              "'" + request.name() + "' — tizimning ichki vositasi nomi, boshqa nom tanlang");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    if (request.parameters() != null) {
      Set<String> seen = new HashSet<>();
      for (ToolParameterRequest p : request.parameters()) {
        if (!seen.add(p.name())) {
          IllegalArgumentException e =
              new IllegalArgumentException("Parametr nomi takrorlangan: " + p.name());
          log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
          throw e;
        }
        if (!PARAMETER_TYPES.contains(p.type())) {
          IllegalArgumentException e =
              new IllegalArgumentException(
                  "Parametr turi noto'g'ri: "
                      + p.type()
                      + " (ruxsat etilgan: "
                      + String.join(", ", PARAMETER_TYPES)
                      + ")");
          log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
          throw e;
        }
      }
    }
    if (request.type() == ToolType.REST_API) {
      parseConfiguration(request.configuration());
      RestToolConfig config;
      try {
        config = RestToolConfig.parse(objectMapper, request.configuration(), restApiProperties);
      } catch (IllegalArgumentException e) {
        IllegalArgumentException ex =
            new IllegalArgumentException("REST_API konfiguratsiyasi noto'g'ri: " + e.getMessage());
        log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
        throw ex;
      }

      if (config.connection() != null) {
        if (!connectionService.exists(orgId, config.connection())) {
          IllegalArgumentException e =
              new IllegalArgumentException(
                  "Bu tashkilotda '" + config.connection() + "' nomli ulanish yo'q");
          log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
          throw e;
        }
      } else {
        outboundUrlPolicy.requireAllowed(
            UriComponentsBuilder.fromUriString(config.endpoint())
                .replacePath(null)
                .replaceQuery(null)
                .build()
                .toUriString());
      }
      if (request.parameters() != null) {
        Set<String> declared = new HashSet<>();
        request.parameters().forEach(p -> declared.add(p.name()));
        for (String var : config.pathVariables()) {
          if (!declared.contains(var)) {
            IllegalArgumentException e =
                new IllegalArgumentException(
                    "Manzildagi {" + var + "} uchun parametr e'lon qilinmagan");
            log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw e;
          }
        }
      }
    } else {
      parseConfiguration(request.configuration());
    }
  }

  private String normalizeConfiguration(String configuration) {
    return configuration == null || configuration.isBlank() ? null : configuration.trim();
  }

  private JsonNode parseConfiguration(String configuration) {
    if (configuration == null || configuration.isBlank()) {
      return null;
    }
    try {
      JsonNode node = objectMapper.readTree(configuration);
      if (!node.isObject()) {
        IllegalArgumentException e =
            new IllegalArgumentException("Konfiguratsiya JSON obyekt bo'lishi kerak ({ ... })");
        log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
        throw e;
      }
      return node;
    } catch (JacksonException e) {
      IllegalArgumentException ex =
          new IllegalArgumentException("Konfiguratsiya JSON noto'g'ri: " + e.getOriginalMessage());
      log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
      throw ex;
    }
  }
}
