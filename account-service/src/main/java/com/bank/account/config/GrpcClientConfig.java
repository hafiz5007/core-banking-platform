package com.bank.account.config;

import com.bank.ledger.grpc.v1.LedgerPostingGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel ledgerChannel(@Value("${ledger.grpc.target:localhost:9090}") String target) {
        log.info("Opening gRPC channel to ledger-service at {}", target);
        return ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
    }

    @Bean
    public LedgerPostingGrpc.LedgerPostingBlockingStub ledgerPostingStub(ManagedChannel ledgerChannel) {
        return LedgerPostingGrpc.newBlockingStub(ledgerChannel);
    }
}
