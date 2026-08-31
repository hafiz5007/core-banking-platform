package com.bank.common.security.grpc;

import com.bank.common.security.ServiceTokenIssuer;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Attaches a freshly minted service token to every outbound gRPC call (ADR-008).
 *
 * <p>The token is minted per call rather than cached: they live ~60s, and minting is a cheap local
 * HMAC, so there is nothing to gain from a cache and a stale token would fail the call.
 */
public class JwtClientInterceptor implements ClientInterceptor {

    /** gRPC metadata keys are lower-case; this is the conventional name for a bearer token. */
    public static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ServiceTokenIssuer issuer;
    private final String audience;
    private final String[] scopes;

    public JwtClientInterceptor(ServiceTokenIssuer issuer, String audience, String... scopes) {
        this.issuer = issuer;
        this.audience = audience;
        this.scopes = scopes.clone();
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(AUTHORIZATION, "Bearer " + issuer.mint(audience, scopes));
                super.start(responseListener, headers);
            }
        };
    }
}
