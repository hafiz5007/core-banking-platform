package com.bank.ledger.adapter.in.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.common.security.ServiceAuthProperties;
import com.bank.common.security.ServiceTokenIssuer;
import com.bank.common.security.grpc.JwtClientInterceptor;
import com.bank.ledger.AbstractIntegrationTest;
import com.bank.ledger.application.LedgerService;
import com.bank.ledger.config.GrpcServerConfig;
import com.bank.ledger.domain.LedgerAccountType;
import com.bank.ledger.grpc.v1.Direction;
import com.bank.ledger.grpc.v1.JournalLine;
import com.bank.ledger.grpc.v1.LedgerPostingGrpc;
import com.bank.ledger.grpc.v1.PostJournalEntryRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * Service-to-service authentication on ledger-service (ADR-008): both transports must refuse a call
 * that does not carry an acceptable token, and accept one that does.
 */
@TestPropertySource(
    properties = {
      "grpc.server.enabled=true",
      "grpc.server.port=0",
      "service-auth.enabled=true",
      "service-auth.service-name=ledger-service",
      "service-auth.secret=" + LedgerPostingServiceAuthIT.SECRET
    })
class LedgerPostingServiceAuthIT extends AbstractIntegrationTest {

  /** Test-only secret; the real one comes from the platform's secret store. */
  static final String SECRET = "integration-test-secret-long-enough-for-hs256";

  private static final Currency USD = Currency.getInstance("USD");

  @Autowired LedgerService ledger;

  @Autowired GrpcServerConfig grpcServer;

  @Autowired TestRestTemplate rest;

  private ManagedChannel channel;
  private String deposits;
  private String settlement;

  private static ServiceTokenIssuer issuerFor(String serviceName, String secret) {
    return new ServiceTokenIssuer(
        new ServiceAuthProperties(
            true,
            secret,
            serviceName,
            "core-banking-platform",
            serviceName,
            Duration.ofSeconds(60),
            Duration.ofSeconds(30)));
  }

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    deposits = "DEP-" + suffix;
    settlement = "SETL-" + suffix;
    ledger.createAccount(deposits, "Customer Deposits", LedgerAccountType.LIABILITY, USD);
    ledger.createAccount(settlement, "Settlement", LedgerAccountType.ASSET, USD);
    channel =
        ManagedChannelBuilder.forAddress("localhost", grpcServer.port()).usePlaintext().build();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  private PostJournalEntryRequest entry() {
    return PostJournalEntryRequest.newBuilder()
        .setIdempotencyKey("auth-" + UUID.randomUUID())
        .setNarrative("Account debit")
        .addLines(
            JournalLine.newBuilder()
                .setAccountCode(deposits)
                .setDirection(Direction.DEBIT)
                .setAmount("10.00"))
        .addLines(
            JournalLine.newBuilder()
                .setAccountCode(settlement)
                .setDirection(Direction.CREDIT)
                .setAmount("10.00"))
        .build();
  }

  private LedgerPostingGrpc.LedgerPostingBlockingStub stubWith(
      ServiceTokenIssuer issuer, String... scopes) {
    return LedgerPostingGrpc.newBlockingStub(channel)
        .withInterceptors(new JwtClientInterceptor(issuer, "ledger-service", scopes));
  }

  private static Status.Code codeOf(Throwable e) {
    return ((StatusRuntimeException) e).getStatus().getCode();
  }

  // --- gRPC ---

  @Test
  void aCallWithAValidTokenIsAccepted() {
    var stub = stubWith(issuerFor("account-service", SECRET), "ledger:post");

    assertThat(stub.postJournalEntry(entry()).getEntryId()).isNotBlank();
  }

  @Test
  void aCallWithNoTokenIsRefused() {
    var stub = LedgerPostingGrpc.newBlockingStub(channel);

    assertThatThrownBy(() -> stub.postJournalEntry(entry()))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(e -> assertThat(codeOf(e)).isEqualTo(Status.Code.UNAUTHENTICATED));
  }

  @Test
  void aCallSignedWithTheWrongSecretIsRefused() {
    var stub =
        stubWith(issuerFor("attacker", "a-completely-different-secret-long-enough"), "ledger:post");

    assertThatThrownBy(() -> stub.postJournalEntry(entry()))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(e -> assertThat(codeOf(e)).isEqualTo(Status.Code.UNAUTHENTICATED));
  }

  @Test
  void aCallWithoutThePostingScopeIsRefused() {
    var stub = stubWith(issuerFor("account-service", SECRET), "ledger:read");

    assertThatThrownBy(() -> stub.postJournalEntry(entry()))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(e -> assertThat(codeOf(e)).isEqualTo(Status.Code.UNAUTHENTICATED));
  }

  @Test
  void aTokenAddressedToAnotherServiceIsRefused() {
    var stub =
        LedgerPostingGrpc.newBlockingStub(channel)
            .withInterceptors(
                new JwtClientInterceptor(
                    issuerFor("account-service", SECRET), "notification-service", "ledger:post"));

    assertThatThrownBy(() -> stub.postJournalEntry(entry()))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(e -> assertThat(codeOf(e)).isEqualTo(Status.Code.UNAUTHENTICATED));
  }

  // --- REST ---

  @Test
  void aRestCallWithNoTokenIsRefused() {
    ResponseEntity<String> response =
        rest.getForEntity("/api/v1/ledger/accounts/" + deposits, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).contains("UNAUTHENTICATED");
  }

  @Test
  void aRestCallWithAValidTokenIsAccepted() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(
        issuerFor("account-service", SECRET).mint("ledger-service", "ledger:write"));

    ResponseEntity<String> response =
        rest.exchange(
            "/api/v1/ledger/accounts/" + deposits,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void healthProbesStayOpenSoOrchestratorsCanReachThem() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
