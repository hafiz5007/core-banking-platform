package com.bank.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Gateway security. In production ({@code gateway.security.enabled=true}) the gateway is an OAuth2
 * resource server: every {@code /api/**} call must carry a valid bearer JWT issued by the bank's
 * identity provider (configure {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}).
 * Actuator endpoints stay open for probes. In dev (the default) the chain permits all so the
 * platform can be run locally without an IdP.
 */
@Configuration
public class SecurityConfig {

  @Bean
  @ConditionalOnProperty(value = "gateway.security.enabled", havingValue = "true")
  SecurityFilterChain securedChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth -> auth.requestMatchers("/actuator/**").permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
    return http.build();
  }

  @Bean
  @ConditionalOnProperty(
      value = "gateway.security.enabled",
      havingValue = "false",
      matchIfMissing = true)
  SecurityFilterChain openChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
