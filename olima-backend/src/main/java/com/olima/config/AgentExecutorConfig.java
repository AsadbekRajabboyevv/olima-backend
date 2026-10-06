package com.olima.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentExecutorConfig {

  public static final String AGENT_EXECUTOR = "agentExecutor";

  @Bean(name = AGENT_EXECUTOR, destroyMethod = "close")
  public ExecutorService agentExecutor() {
    return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("agent-", 0).factory());
  }
}
