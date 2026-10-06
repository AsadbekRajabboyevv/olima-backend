package com.olima.common.config;

import com.olima.common.http.HttpClientProperties;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {

  @Bean
  public RestClient restClient(HttpClientProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(properties.readTimeout());
    return RestClient.builder()
        .requestFactory(factory)
        .defaultHeader("User-Agent", properties.userAgent())
        .build();
  }
}
