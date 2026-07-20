package com.bank.card.adapter.out.ledger;

import com.bank.card.application.port.LedgerPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** HTTP adapter to the ledger service: posts a balanced journal entry for a card settlement. */
@Component
public class HttpLedgerAdapter implements LedgerPort {

    private final RestClient restClient;

    public HttpLedgerAdapter(RestClient.Builder builder,
                             @Value("${ledger.base-url:http://localhost:8083}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public UUID postTransfer(TransferCommand command) {
        PostEntryBody body = new PostEntryBody(
                command.idempotencyKey(), command.narrative(),
                List.of(
                        new LineBody(command.debtorAccount(), "DEBIT", command.amount().amount()),
                        new LineBody(command.creditorAccount(), "CREDIT", command.amount().amount())));
        LedgerEntryRef ref = restClient.post()
                .uri("/api/v1/ledger/entries")
                .body(body)
                .retrieve()
                .body(LedgerEntryRef.class);
        if (ref == null || ref.id() == null) {
            throw new IllegalStateException("Ledger did not return an entry id");
        }
        return ref.id();
    }

    record PostEntryBody(String idempotencyKey, String narrative, List<LineBody> lines) {
    }

    record LineBody(String accountCode, String direction, BigDecimal amount) {
    }

    record LedgerEntryRef(UUID id) {
    }
}
