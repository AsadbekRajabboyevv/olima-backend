package com.olima.agent;

import com.olima.agent.config.AgentProperties;
import java.time.Duration;

final class AgentTestProperties {

  private AgentTestProperties() {}

  static AgentProperties defaults() {
    return new AgentProperties(
        5,
        Duration.ofSeconds(1),
        Duration.ofMinutes(1),
        "",
        new AgentProperties.History(15, 10_000, 1_500),
        new AgentProperties.Messages(
            "Suhbat: {org}", "Tasdiqlandi", "Xato ({ref})", "[qisqartirildi]"));
  }
}
