package com.bank.interestfee.adapter.out.ledger;

import com.bank.common.security.ServiceTokenIssuer;
import com.bank.common.security.web.JwtPropagationInterceptor;
import com.bank.interestfee.application.port.LedgerPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** HTTP adapter to the ledger service: posts a balanced journal entry (debit then credit). */
@Component
public class HttpLedgerAdapter implements LedgerPort {

  private static final String TARGET_AUDIENCE = "ledger-service";
  private static final String REQUIRED_SCOPE = "ledger:post";

  private final RestClient restClient;

  public HttpLedgerAdapter(
      RestClient.Builder builder,
      @Value("${ledger.base-url:http://localhost:8083}") String baseUrl,
      ObjectProvider<ServiceTokenIssuer> issuer) {
    RestClient.Builder configured = builder.baseUrl(baseUrl);
    // Carry this service's identity on the call when service auth is enabled (ADR-008).
    ServiceTokenIssuer tokenIssuer = issuer.getIfAvailable();
    if (tokenIssuer != null) {
      configured =
          configured.requestInterceptor(
              new JwtPropagationInterceptor(tokenIssuer, TARGET_AUDIENCE, REQUIRED_SCOPE));
    }
    this.restClient = configured.build();
  }

  @Override
  public UUID postTransfer(TransferCommand command) {
    PostEntryBody body =
        new PostEntryBody(
            command.idempotencyKey(),
            command.narrative(),
            List.of(
                new LineBody(command.debtorAccount(), "DEBIT", command.amount().amount()),
                new LineBody(command.creditorAccount(), "CREDIT", command.amount().amount())));
    LedgerEntryRef ref =
        restClient
            .post()
            .uri("/api/v1/ledger/entries")
            .body(body)
            .retrieve()
            .body(LedgerEntryRef.class);
    if (ref == null || ref.id() == null) {
      throw new IllegalStateException("Ledger did not return an entry id");
    }
    return ref.id();
  }

  record PostEntryBody(String idempotencyKey, String narrative, List<LineBody> lines) {}

  record LineBody(String accountCode, String direction, BigDecimal amount) {}

  record LedgerEntryRef(UUID id) {}
}
