package com.olima.integration;

import com.olima.integration.dto.ConnectionRequest;
import com.olima.integration.dto.ConnectionResponse;
import com.olima.integration.dto.ConnectionSnapshot;
import com.olima.integration.dto.ConnectionTestResponse;
import java.util.List;
import java.util.UUID;

public interface IntegrationConnectionService {

  List<ConnectionResponse> findByOrganization(UUID organizationId);

  ConnectionResponse findById(UUID id);

  ConnectionResponse create(UUID organizationId, ConnectionRequest request);

  ConnectionResponse update(UUID id, ConnectionRequest request);

  void delete(UUID id);

  ConnectionTestResponse test(UUID id);

  ConnectionSnapshot snapshot(UUID organizationId, String name);

  boolean exists(UUID organizationId, String name);
}
