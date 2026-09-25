package com.bank.ledger.config;

import com.bank.common.security.ServiceTokenVerifier;
import com.bank.common.security.grpc.JwtServerInterceptor;
import io.grpc.ServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The gRPC half of ledger-service's inbound guard (ADR-008).
 *
 * <p>The token beans and the REST filter come from common-lib's auto-configuration; only the gRPC
 * interceptor is declared here, because the scope it demands is specific to this service.
 */
@Configuration
@ConditionalOnProperty(
    name = {"service-auth.enabled", "service-auth.require-inbound"},
    havingValue = "true")
public class ServiceAuthConfig {

  /** Scope a caller must hold to post journal entries. */
  public static final String SCOPE_LEDGER_POST = "ledger:post";

  @Bean
  public ServerInterceptor ledgerPostingAuthInterceptor(ServiceTokenVerifier verifier) {
    return new JwtServerInterceptor(verifier, SCOPE_LEDGER_POST);
  }
}
