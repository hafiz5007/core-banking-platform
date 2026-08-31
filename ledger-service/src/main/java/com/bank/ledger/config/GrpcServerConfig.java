package com.bank.ledger.config;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Runs the gRPC server alongside the HTTP one, on its own port. Enabled with
 * {@code grpc.server.enabled=true} so REST-only deployments are unchanged.
 *
 * <p>A {@link SmartLifecycle} rather than a plain bean so the port is bound after the context is
 * ready and released on shutdown; tests bind port 0 and read the assigned port back.
 */
@Component
@ConditionalOnProperty(name = "grpc.server.enabled", havingValue = "true")
public class GrpcServerConfig implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);

    private final List<BindableService> services;
    private final int configuredPort;
    private final long shutdownTimeoutSeconds;

    private Server server;

    public GrpcServerConfig(List<BindableService> services,
                            @Value("${grpc.server.port:9090}") int configuredPort,
                            @Value("${grpc.server.shutdown-timeout-seconds:10}") long shutdownTimeoutSeconds) {
        this.services = services;
        this.configuredPort = configuredPort;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    }

    @Override
    public void start() {
        if (isRunning()) {
            return;
        }
        ServerBuilder<?> builder = ServerBuilder.forPort(configuredPort);
        services.forEach(builder::addService);
        try {
            server = builder.build().start();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to start the gRPC server on port " + configuredPort, e);
        }
        log.info("gRPC server listening on port {} with {} service(s)", server.getPort(), services.size());
    }

    @Override
    public void stop() {
        if (server == null) {
            return;
        }
        try {
            server.shutdown().awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server = null;
        }
    }

    @Override
    public boolean isRunning() {
        return server != null && !server.isShutdown();
    }

    /** The bound port; with {@code grpc.server.port=0} this is the one the OS assigned. */
    public int port() {
        if (server == null) {
            throw new IllegalStateException("gRPC server is not running");
        }
        return server.getPort();
    }
}
