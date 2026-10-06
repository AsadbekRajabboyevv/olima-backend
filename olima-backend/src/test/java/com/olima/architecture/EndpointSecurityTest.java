package com.olima.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class EndpointSecurityTest {

  private static final Set<String> PUBLIC_BY_DESIGN =
      Set.of(
          "AuthController#login",
          "AuthController#refresh",
          "AuthController#logout",
          "AgentController#chat",
          "AgentController#chatStream",
          "AgentController#confirmAction",
          "WidgetConfigController#config",
          "TelegramWebhookController#receiveUpdate");

  @Test
  void everyEndpointDeclaresAuthorization() throws Exception {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    List<String> unprotected = new ArrayList<>();
    for (var candidate : scanner.findCandidateComponents("com.olima")) {
      Class<?> controller = Class.forName(candidate.getBeanClassName());
      boolean classProtected = controller.isAnnotationPresent(PreAuthorize.class);
      for (Method method : controller.getDeclaredMethods()) {
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
          continue;
        }
        String id = controller.getSimpleName() + "#" + method.getName();
        if (!classProtected
            && !method.isAnnotationPresent(PreAuthorize.class)
            && !PUBLIC_BY_DESIGN.contains(id)) {
          unprotected.add(id);
        }
      }
    }
    assertThat(unprotected)
        .as("Endpoints without @PreAuthorize (add a tenant check or list them as public)")
        .isEmpty();
  }
}
