package com.olima.organization;

import com.olima.organization.dto.WidgetConfigResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

public interface WidgetConfigApi {

  @GetMapping("/api/v1/chat/widget-config")
  ResponseEntity<WidgetConfigResponse> config(HttpServletRequest request);
}
