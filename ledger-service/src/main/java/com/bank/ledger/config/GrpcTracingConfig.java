package com.bank.ledger.config;

import com.bank.common.web.grpc.CorrelationIdServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Registers correlation-id propagation on the inbound gRPC path.
 *
 * <p>{@link GrpcServerConfig} injects every {@code ServerInterceptor} bean and hands the list to
 * {@code ServerInterceptors.intercept}, which applies a list in reverse — the last element becomes
 * the outermost interceptor. Ordering this one last therefore makes it run first, so a call rejected
 * by the auth interceptor is still logged against the caller's correlation id rather than anonymously.
 */
@Configuration
@ConditionalOnProperty(name = "grpc.server.enabled", havingValue = "true")
public class GrpcTracingConfig {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public CorrelationIdServerInterceptor correlationIdServerInterceptor() {
        return new CorrelationIdServerInterceptor();
    }
}
