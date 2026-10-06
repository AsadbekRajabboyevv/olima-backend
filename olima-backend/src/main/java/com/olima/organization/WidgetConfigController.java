package com.olima.organization;

import com.olima.organization.dto.WidgetConfigResponse;
import com.olima.organization.resolver.WidgetContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WidgetConfigController implements WidgetConfigApi {

  private final OrganizationService organizationService;
  private final WidgetContextResolver widgetContextResolver;

  @Override
  @GetMapping("/api/v1/chat/widget-config")
  public ResponseEntity<WidgetConfigResponse> config(HttpServletRequest request) {
    return widgetContextResolver
        .resolveOrganizationId(request)
        .map(organizationService::widgetConfig)
        .map(config -> ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(config))
        .orElseGet(() -> ResponseEntity.badRequest().build());
  }
}
