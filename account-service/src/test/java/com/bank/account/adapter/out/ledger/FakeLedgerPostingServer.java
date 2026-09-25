package com.bank.account.adapter.out.ledger;

import com.bank.ledger.grpc.v1.LedgerPostingGrpc;
import com.bank.ledger.grpc.v1.PostJournalEntryRequest;
import com.bank.ledger.grpc.v1.PostJournalEntryResponse;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A real gRPC server speaking the ledger contract, standing in for ledger-service.
 *
 * <p>Real Netty transport on a real port — the account-service side is exercised over the wire, not
 * against a mock. Records what it received so tests can assert on the actual protobuf, and can be
 * told to fail with a chosen status to prove the client's error mapping.
 */
public final class FakeLedgerPostingServer implements AutoCloseable {

  private final List<PostJournalEntryRequest> received = new CopyOnWriteArrayList<>();
  private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
  private final AtomicReference<Status> failWith = new AtomicReference<>();
  private final Server server;

  public FakeLedgerPostingServer() {
    try {
      this.server =
          ServerBuilder.forPort(0)
              .addService(io.grpc.ServerInterceptors.intercept(new Service(), new CaptureAuth()))
              .build()
              .start();
    } catch (IOException e) {
      throw new IllegalStateException("Could not start the fake ledger gRPC server", e);
    }
  }

  public String target() {
    return "localhost:" + server.getPort();
  }

  public List<PostJournalEntryRequest> received() {
    return received;
  }

  public PostJournalEntryRequest lastRequest() {
    if (received.isEmpty()) {
      throw new AssertionError("The fake ledger received no gRPC call");
    }
    return received.get(received.size() - 1);
  }

  /** Authorization metadata seen on each call, so tests can assert the client really sent one. */
  public List<String> authorizationHeaders() {
    return authorizationHeaders;
  }

  public void reset() {
    received.clear();
    authorizationHeaders.clear();
    failWith.set(null);
  }

  /** Make the next calls fail with this status, to exercise the client's error translation. */
  public void failWith(Status status) {
    failWith.set(status);
  }

  @Override
  public void close() {
    try {
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Records the authorization metadata of every inbound call. */
  private final class CaptureAuth implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
      String header = headers.get(AUTHORIZATION);
      authorizationHeaders.add(header == null ? "" : header);
      return next.startCall(call, headers);
    }
  }

  private final class Service extends LedgerPostingGrpc.LedgerPostingImplBase {
    @Override
    public void postJournalEntry(
        PostJournalEntryRequest request, StreamObserver<PostJournalEntryResponse> observer) {
      received.add(request);
      Status failure = failWith.get();
      if (failure != null) {
        observer.onError(failure.withDescription("fake ledger failure").asRuntimeException());
        return;
      }
      observer.onNext(
          PostJournalEntryResponse.newBuilder()
              .setEntryId(UUID.randomUUID().toString())
              .setStatus("POSTED")
              .build());
      observer.onCompleted();
    }
  }
}
