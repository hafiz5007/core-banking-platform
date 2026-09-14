package com.bank.account.config;

import com.bank.common.security.ServiceTokenIssuer;
import com.bank.common.security.grpc.JwtClientInterceptor;
import com.bank.common.web.grpc.CorrelationIdClientInterceptor;
import com.bank.ledger.grpc.v1.LedgerPostingGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC channel to ledger-service, created only when {@code ledger.posting=grpc}.
 *
 * <p>The channel is a long-lived, thread-safe, multiplexing connection — one per application, not
 * one per call. Spring closes it on shutdown via the bean's {@code destroyMethod}.
 *
 * <p>Plaintext: in-cluster traffic is expected to be secured by the service mesh (see the
 * service-to-service item in {@code docs/PENDING_TASKS.md}). Swap in TLS credentials here when the
 * mesh is not providing mTLS.
 */
@Configuration
@ConditionalOnProperty(name = "ledger.posting", havingValue = "grpc")
public class GrpcClientConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcClientConfig.class);

    /** ledger-service accepts tokens addressed to it under this name. */
    public static final String LEDGER_AUDIENCE = "ledger-service";

    /** Scope ledger-service requires to accept a posting. */
    public static final String SCOPE_LEDGER_POST = "ledger:post";

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel ledgerChannel(@Value("${ledger.grpc.target:localhost:9090}") String target) {
        log.info("Opening gRPC channel to ledger-service at {}", target);
        return ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
    }

    /**
     * The stub, with a service-token interceptor attached when service auth is enabled (ADR-008).
     * The issuer bean only exists when {@code service-auth.enabled=true}, so an unsecured deployment
     * simply gets a plain stub.
     */
    @Bean
    public LedgerPostingGrpc.LedgerPostingBlockingStub ledgerPostingStub(
            ManagedChannel ledgerChannel, ObjectProvider<ServiceTokenIssuer> issuer) {
        LedgerPostingGrpc.LedgerPostingBlockingStub stub = LedgerPostingGrpc.newBlockingStub(ledgerChannel)
                // Tracing is attached unconditionally: following a request across the hop must not
                // depend on whether service auth happens to be switched on.
                .withInterceptors(new CorrelationIdClientInterceptor());
        ServiceTokenIssuer tokenIssuer = issuer.getIfAvailable();
        if (tokenIssuer == null) {
            log.warn("Service auth is disabled: calls to ledger-service carry no identity");
            return stub;
        }
        return stub.withInterceptors(
                new JwtClientInterceptor(tokenIssuer, LEDGER_AUDIENCE, SCOPE_LEDGER_POST));
    }
}
