package com.bank.payment.adapter.out.ledger;

import com.bank.payment.application.port.LedgerPort;
import com.bank.common.security.ServiceTokenIssuer;
import com.bank.common.security.web.JwtPropagationInterceptor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP adapter to the ledger service. Translates a payment transfer into a balanced journal entry
 * (debit debtor, credit creditor) and calls the ledger's REST API. Any non-2xx response surfaces as
 * a {@link RuntimeException}, which the saga treats as a failed posting.
 */
@Component
public class HttpLedgerAdapter implements LedgerPort {

    private static final String TARGET_AUDIENCE = "ledger-service";
    private static final String REQUIRED_SCOPE = "ledger:post";

    private final RestClient restClient;

    public HttpLedgerAdapter(RestClient.Builder builder,
                             @Value("${ledger.base-url:http://localhost:8083}") String baseUrl,
                             ObjectProvider<ServiceTokenIssuer> issuer) {
        RestClient.Builder configured = builder.baseUrl(baseUrl);
        // Carry this service's identity on the call when service auth is enabled (ADR-008).
        ServiceTokenIssuer tokenIssuer = issuer.getIfAvailable();
        if (tokenIssuer != null) {
            configured = configured.requestInterceptor(
                    new JwtPropagationInterceptor(tokenIssuer, TARGET_AUDIENCE, REQUIRED_SCOPE));
        }
        this.restClient = configured.build();
    }

    @Override
    public UUID postTransfer(TransferCommand command) {
        PostEntryBody body = new PostEntryBody(
                command.idempotencyKey(),
                command.narrative(),
                List.of(
                        new LineBody(command.debtorAccount(), "DEBIT", command.amount().amount()),
                        new LineBody(command.creditorAccount(), "CREDIT", command.amount().amount())));
        LedgerEntryRef ref = restClient.post()
                .uri("/api/v1/ledger/entries")
                .body(body)
                .retrieve()
                .body(LedgerEntryRef.class);
        return requireId(ref);
    }

    @Override
    public UUID reverse(UUID ledgerEntryId, String idempotencyKey) {
        LedgerEntryRef ref = restClient.post()
                .uri("/api/v1/ledger/entries/{id}/reversal", ledgerEntryId)
                .body(new ReversalBody(idempotencyKey))
                .retrieve()
                .body(LedgerEntryRef.class);
        return requireId(ref);
    }

    private UUID requireId(LedgerEntryRef ref) {
        if (ref == null || ref.id() == null) {
            throw new IllegalStateException("Ledger did not return an entry id");
        }
        return ref.id();
    }

    // --- wire records ---

    record PostEntryBody(String idempotencyKey, String narrative, List<LineBody> lines) {
    }

    record LineBody(String accountCode, String direction, BigDecimal amount) {
    }

    record ReversalBody(String idempotencyKey) {
    }

    record LedgerEntryRef(UUID id) {
    }
}
