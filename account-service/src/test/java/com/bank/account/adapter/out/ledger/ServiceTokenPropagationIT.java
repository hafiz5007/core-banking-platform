package com.bank.account.adapter.out.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.account.AbstractIntegrationTest;
import com.bank.account.adapter.in.web.dto.AccountResponse;
import com.bank.account.adapter.in.web.dto.CreateProductRequest;
import com.bank.account.adapter.in.web.dto.DebitAccountRequest;
import com.bank.account.adapter.in.web.dto.OpenFromProductRequest;
import com.bank.account.adapter.in.web.dto.ProductResponse;
import com.bank.account.domain.AccountType;
import com.bank.account.domain.MandateType;
import com.bank.common.security.ServiceAuthProperties;
import com.bank.common.security.ServiceTokenIssuer;
import com.bank.common.security.ServiceTokenVerifier;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The calling half of service auth (ADR-008): account-service must put a token ledger-service would
 * accept on its outbound gRPC calls.
 */
class ServiceTokenPropagationIT extends AbstractIntegrationTest {

    /** Test-only secret; the real one comes from the platform's secret store. */
    private static final String SECRET = "integration-test-secret-long-enough-for-hs256";

    private static final FakeLedgerPostingServer LEDGER = new FakeLedgerPostingServer();

    @DynamicPropertySource
    static void enableServiceAuth(DynamicPropertyRegistry registry) {
        registry.add("ledger.posting", () -> "grpc");
        registry.add("ledger.grpc.target", LEDGER::target);
        registry.add("service-auth.enabled", () -> "true");
        registry.add("service-auth.service-name", () -> "account-service");
        registry.add("service-auth.secret", () -> SECRET);
    }

    @AfterAll
    static void stopLedger() {
        LEDGER.close();
    }

    @Autowired
    TestRestTemplate rest;

    @BeforeEach
    void resetLedger() {
        LEDGER.reset();
    }

    /** A verifier configured exactly as ledger-service would be. */
    private static ServiceTokenVerifier asLedgerService() {
        return new ServiceTokenVerifier(new ServiceAuthProperties(true, SECRET, "ledger-service",
                "core-banking-platform", "ledger-service", Duration.ofSeconds(60), Duration.ofSeconds(30)));
    }

    private AccountResponse openAccountWithOverdraft() {
        String code = "CUR-" + UUID.randomUUID().toString().substring(0, 8);
        rest.postForEntity("/api/v1/products",
                new CreateProductRequest(code, "Current with overdraft", AccountType.CURRENT, "USD",
                        new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                        new BigDecimal("10000.00"), new BigDecimal("500.00")),
                ProductResponse.class);
        return rest.postForEntity("/api/v1/accounts/from-product",
                new OpenFromProductRequest(code, UUID.randomUUID(), List.of(), MandateType.SINGLE),
                AccountResponse.class).getBody();
    }

    private void debit(UUID accountId, String amount) {
        rest.postForEntity("/api/v1/accounts/" + accountId + "/debit",
                new DebitAccountRequest("debit-" + UUID.randomUUID(), new BigDecimal(amount), "USD", "ATM"),
                AccountResponse.class);
    }

    @Test
    void outboundCallsCarryABearerToken() {
        AccountResponse account = openAccountWithOverdraft();

        debit(account.id(), "25.00");

        assertThat(LEDGER.authorizationHeaders()).hasSize(1);
        assertThat(LEDGER.authorizationHeaders().get(0)).startsWith("Bearer ");
    }

    @Test
    void theTokenIsOneLedgerServiceWouldAccept() {
        AccountResponse account = openAccountWithOverdraft();

        debit(account.id(), "25.00");

        String token = LEDGER.authorizationHeaders().get(0).substring("Bearer ".length());
        var claims = asLedgerService().verify(token, "ledger:post");

        assertThat(claims.getSubject()).isEqualTo("account-service");
        assertThat(claims.getAudience()).containsExactly("ledger-service");
        assertThat(claims.getClaim(ServiceTokenIssuer.CLAIM_SCOPE).toString()).contains("ledger:post");
    }

    @Test
    void eachCallGetsItsOwnFreshToken() {
        AccountResponse account = openAccountWithOverdraft();

        debit(account.id(), "10.00");
        debit(account.id(), "11.00");

        assertThat(LEDGER.authorizationHeaders()).hasSize(2);
        // Distinct jti per call: tokens are minted per request, not cached and replayed.
        assertThat(LEDGER.authorizationHeaders().get(0))
                .isNotEqualTo(LEDGER.authorizationHeaders().get(1));
    }
}
