package com.bank.common.security;

import com.bank.common.security.web.ServiceTokenAuthFilter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Wires service-to-service authentication (ADR-008) into any service that puts common-lib on its
 * classpath, so a service opts in with configuration rather than code.
 *
 * <p>Two independent switches:
 *
 * <ul>
 *   <li>{@code service-auth.enabled} — mint and verify are available. A <em>caller</em> needs only
 *       this.
 *   <li>{@code service-auth.require-inbound} — inbound calls must present a token. A
 *       <em>receiver</em> sets this too. It is separate because turning it on refuses every caller
 *       that is not yet wired, so a rollout enables callers first and receivers second.
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "service-auth.enabled", havingValue = "true")
public class ServiceAuthAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ServiceAuthProperties serviceAuthProperties(
      @Value("${service-auth.secret}") String secret,
      @Value("${service-auth.service-name:${spring.application.name}}") String serviceName,
      @Value("${service-auth.issuer:core-banking-platform}") String issuer,
      @Value("${service-auth.token-ttl-seconds:60}") long tokenTtlSeconds,
      @Value("${service-auth.clock-skew-seconds:30}") long clockSkewSeconds) {
    // A service accepts tokens addressed to its own name, and mints tokens claiming it.
    return new ServiceAuthProperties(
        true,
        secret,
        serviceName,
        issuer,
        serviceName,
        Duration.ofSeconds(tokenTtlSeconds),
        Duration.ofSeconds(clockSkewSeconds));
  }

  @Bean
  @ConditionalOnMissingBean
  public ServiceTokenIssuer serviceTokenIssuer(ServiceAuthProperties properties) {
    return new ServiceTokenIssuer(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public ServiceTokenVerifier serviceTokenVerifier(ServiceAuthProperties properties) {
    return new ServiceTokenVerifier(properties);
  }

  /**
   * Receiver-side REST guard. Only registered by services that require inbound identity.
   *
   * <p>No {@code @ConditionalOnMissingBean} here: services already register FilterRegistrationBeans
   * for the correlation-id and tenant filters, and the condition matches on the raw type, so it
   * would silently skip this one and leave the service unguarded.
   */
  @Bean
  @ConditionalOnClass(FilterRegistrationBean.class)
  @ConditionalOnProperty(name = "service-auth.require-inbound", havingValue = "true")
  public FilterRegistrationBean<ServiceTokenAuthFilter> serviceTokenAuthFilter(
      ServiceTokenVerifier verifier) {
    FilterRegistrationBean<ServiceTokenAuthFilter> registration =
        new FilterRegistrationBean<>(new ServiceTokenAuthFilter(verifier));
    // After the correlation-id filter, so a rejection is still traceable.
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return registration;
  }
}
