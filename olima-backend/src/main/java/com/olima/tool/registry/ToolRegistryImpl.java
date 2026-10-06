package com.olima.tool.registry;

import com.olima.tool.ToolEntity;
import com.olima.tool.ToolRepository;
import com.olima.tool.ToolServiceImpl;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ToolRegistryImpl implements ToolRegistry {

  private final ToolRepository toolRepository;

  @Override
  @Cacheable(cacheNames = ToolServiceImpl.ENABLED_TOOLS_CACHE, key = "#organizationId")
  public List<ToolEntity> getEnabledTools(UUID organizationId) {
    List<ToolEntity> tools = toolRepository.findByOrganizationIdAndEnabledTrue(organizationId);
    return tools;
  }
}
