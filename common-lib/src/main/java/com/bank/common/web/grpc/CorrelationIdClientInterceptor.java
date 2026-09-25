package com.bank.common.web.grpc;

import com.bank.common.web.CorrelationIdFilter;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.slf4j.MDC;

/**
 * Carries the correlation id across an outbound gRPC call.
 *
 * <p>{@link CorrelationIdFilter} propagates the id across REST hops, but a gRPC call is a separate
 * transport with its own metadata: without this interceptor the id stops at the service boundary
 * and a request can only be followed into ledger-service by matching on entry id or narrative. The
 * metadata key mirrors the HTTP header name in lower case, as gRPC requires.
 *
 * <p>Attached unconditionally — tracing must not depend on whether service auth is switched on.
 */
public class CorrelationIdClientInterceptor implements ClientInterceptor {

  /** gRPC metadata keys must be lower-case; this is {@code X-Correlation-Id} in gRPC form. */
  public static final Metadata.Key<String> CORRELATION_ID =
      Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall<>(
        next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        // Read the MDC directly rather than CorrelationIdFilter.current(), which substitutes
        // "n/a" when absent. Sending a literal "n/a" downstream would be worse than sending
        // nothing: the receiver would adopt it as a real id and several unrelated requests
        // would share it.
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
          headers.put(CORRELATION_ID, correlationId);
        }
        super.start(responseListener, headers);
      }
    };
  }
}
