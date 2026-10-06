package com.olima.common.config;

import com.olima.common.error.ErrorCode;
import com.olima.common.error.ProblemResponses;
import com.olima.common.web.Paging;
import com.olima.common.web.RequestIdFilter;
import com.olima.security.config.SecurityProperties;
import com.olima.security.filter.JwtAuthenticationFilter;
import com.olima.security.filter.WidgetKeyFilter;
import com.olima.security.ratelimit.RateLimitFilter;
import com.olima.user.enums.UserRole;
import jakarta.servlet.Filter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private static final String[] ADMIN_ROLES = {
    UserRole.SUPER_ADMIN.name(), UserRole.ORG_ADMIN.name()
  };

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final WidgetKeyFilter widgetKeyFilter;
  private final RateLimitFilter rateLimitFilter;
  private final ProblemResponses problems;
  private final SecurityProperties securityProperties;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers ->
                headers.referrerPolicy(
                    ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout")
                    .permitAll()
                    .requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .hasRole(UserRole.SUPER_ADMIN.name())
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/telegram/webhook/**")
                    .permitAll()
                    .requestMatchers("/api/v1/chat/**")
                    .hasAnyRole(
                        UserRole.WIDGET.name(),
                        UserRole.SUPER_ADMIN.name(),
                        UserRole.ORG_ADMIN.name())
                    .requestMatchers("/api/**")
                    .hasAnyRole(ADMIN_ROLES)
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, authException) ->
                            problems.write(
                                response, ErrorCode.UNAUTHORIZED, "Authentication is required"))
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) ->
                            problems.write(
                                response,
                                ErrorCode.FORBIDDEN,
                                "You do not have permission to perform this action")))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(widgetKeyFilter, JwtAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, WidgetKeyFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(securityProperties.cors().allowedOriginPatterns());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setExposedHeaders(
        List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.RETRY_AFTER,
            Paging.TOTAL_COUNT,
            Paging.PAGE,
            Paging.PAGE_SIZE,
            RequestIdFilter.HEADER));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
      JwtAuthenticationFilter filter) {
    return disabled(filter);
  }

  @Bean
  FilterRegistrationBean<WidgetKeyFilter> widgetFilterRegistration(WidgetKeyFilter filter) {
    return disabled(filter);
  }

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
    return disabled(filter);
  }

  private static <T extends Filter> FilterRegistrationBean<T> disabled(T filter) {
    FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
