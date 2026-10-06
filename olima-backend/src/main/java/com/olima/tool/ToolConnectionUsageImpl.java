package com.olima.tool;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ToolConnectionUsageImpl implements ToolConnectionUsage {

  private final ToolRepository toolRepository;

  @Override
  @Transactional(readOnly = true)
  public long countToolsUsing(UUID organizationId, String connectionName) {
    return toolRepository.countByConnection(organizationId, connectionName);
  }
}
