package com.bank.common.web.grpc;

import com.bank.common.web.CorrelationIdFilter;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Adopts the caller's correlation id on an inbound gRPC call, so log lines on this side of the hop
 * carry the same id as the caller's.
 *
 * <p>The id is put in the gRPC {@link Context} — which follows the call — and into the SLF4J MDC
 * around every listener callback. The MDC is a {@code ThreadLocal} and gRPC may run {@code
 * startCall} and the subsequent callbacks on different threads, so setting it once in {@code
 * interceptCall} would leak the value onto a pooled thread and lose it on the thread that actually
 * runs the handler. Each callback therefore sets and clears it itself.
 *
 * <p>A call arriving without an id gets a fresh one, exactly as {@link CorrelationIdFilter} does
 * for an untagged HTTP request.
 */
public class CorrelationIdServerInterceptor implements ServerInterceptor {

  /** The correlation id for the current call, available to handlers. */
  public static final Context.Key<String> CORRELATION_ID = Context.key("correlationId");

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    String inbound = headers.get(CorrelationIdClientInterceptor.CORRELATION_ID);
    String correlationId =
        (inbound == null || inbound.isBlank()) ? UUID.randomUUID().toString() : inbound;

    Context context = Context.current().withValue(CORRELATION_ID, correlationId);
    ServerCall.Listener<ReqT> delegate = Contexts.interceptCall(context, call, headers, next);
    return new MdcScopedListener<>(delegate, correlationId);
  }

  /** Wraps each callback so the handler always runs with the id in the MDC, on whatever thread. */
  private static final class MdcScopedListener<ReqT>
      extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

    private final String correlationId;

    private MdcScopedListener(ServerCall.Listener<ReqT> delegate, String correlationId) {
      super(delegate);
      this.correlationId = correlationId;
    }

    private void scoped(Runnable action) {
      MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
      try {
        action.run();
      } finally {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
      }
    }

    @Override
    public void onMessage(ReqT message) {
      scoped(() -> super.onMessage(message));
    }

    @Override
    public void onHalfClose() {
      scoped(super::onHalfClose);
    }

    @Override
    public void onCancel() {
      scoped(super::onCancel);
    }

    @Override
    public void onComplete() {
      scoped(super::onComplete);
    }

    @Override
    public void onReady() {
      scoped(super::onReady);
    }
  }
}
