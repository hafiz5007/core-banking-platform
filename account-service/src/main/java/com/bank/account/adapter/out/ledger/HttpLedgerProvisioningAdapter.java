package com.bank.account.adapter.out.ledger;

import com.bank.account.application.port.LedgerProvisioningPort;
import java.util.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Creates a customer-deposit GL account (a LIABILITY) in ledger-service when an account is opened.
 * Enabled with {@code ledger.provisioning=http}. A 409 (account already exists) is treated as
 * idempotent success; other failures propagate so the account open fails loudly rather than leaving
 * an account with no ledger backing.
 */
@Component
@ConditionalOnProperty(name = "ledger.provisioning", havingValue = "http")
public class HttpLedgerProvisioningAdapter implements LedgerProvisioningPort {

    private static final Logger log = LoggerFactory.getLogger(HttpLedgerProvisioningAdapter.class);

    private final RestClient restClient;

    public HttpLedgerProvisioningAdapter(RestClient.Builder builder,
                                         @Value("${ledger.base-url:http://localhost:8083}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public void provisionAccount(String code, String name, Currency currency) {
        try {
            restClient.post()
                    .uri("/api/v1/ledger/accounts")
                    .body(new CreateLedgerAccountBody(code, name, "LIABILITY", currency.getCurrencyCode()))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Provisioned GL account {} in ledger", code);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                log.info("GL account {} already exists; provisioning is idempotent", code);
                return;
            }
            throw e;
        }
    }

    record CreateLedgerAccountBody(String code, String name, String accountType, String currencyCode) {
    }
}
