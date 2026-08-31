package com.bank.common.security.grpc;

import com.bank.common.security.ServiceTokenVerifier;
import com.bank.common.security.ServiceTokenVerifier.InvalidServiceTokenException;
import com.nimbusds.jwt.JWTClaimsSet;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rejects inbound gRPC calls that do not carry an acceptable service token (ADR-008).
 *
 * <p>Runs before the handler, so an unauthenticated call never reaches business code. The calling
 * service's name is placed in the gRPC {@link Context} for handlers and logging.
 */
public class JwtServerInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtServerInterceptor.class);

    /** The verified caller identity, available to handlers for the duration of the call. */
    public static final Context.Key<String> CALLER_SERVICE = Context.key("callerService");

    private static final String BEARER = "Bearer ";

    private final ServiceTokenVerifier verifier;
    private final String requiredScope;

    public JwtServerInterceptor(ServiceTokenVerifier verifier, String requiredScope) {
        this.verifier = verifier;
        this.requiredScope = requiredScope;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String header = headers.get(JwtClientInterceptor.AUTHORIZATION);
        String token = header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()) : header;
        try {
            JWTClaimsSet claims = verifier.verify(token, requiredScope);
            Context context = Context.current().withValue(CALLER_SERVICE, claims.getSubject());
            return Contexts.interceptCall(context, call, headers, next);
        } catch (InvalidServiceTokenException e) {
            // The reason is safe to return: it describes the token, never its contents.
            log.warn("Rejected gRPC call to {}: {}", call.getMethodDescriptor().getFullMethodName(), e.getMessage());
            call.close(Status.UNAUTHENTICATED.withDescription(e.getMessage()), new Metadata());
            return new ServerCall.Listener<>() { };
        }
    }
}
