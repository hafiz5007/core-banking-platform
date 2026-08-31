package com.bank.riskaml.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.common.security.ServiceAuthProperties;
import com.bank.common.security.ServiceTokenIssuer;
import com.bank.riskaml.AbstractIntegrationTest;
import com.bank.riskaml.adapter.in.web.RiskController.EvaluateRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * risk-aml-service as a receiver (ADR-008): payment-service calls {@code /api/v1/risk/evaluate} on
 * the payment hot path, so that call must carry an identity.
 */
@TestPropertySource(properties = {
        "service-auth.enabled=true",
        "service-auth.require-inbound=true",
        "service-auth.service-name=risk-aml-service",
        "service-auth.secret=" + RiskServiceAuthIT.SECRET
})
class RiskServiceAuthIT extends AbstractIntegrationTest {

    /** Test-only secret; the real one comes from the environment. */
    static final String SECRET = "integration-test-secret-long-enough-for-hs256";

    @Autowired
    TestRestTemplate rest;

    private static ServiceTokenIssuer issuerFor(String serviceName, String secret) {
        return new ServiceTokenIssuer(new ServiceAuthProperties(true, secret, serviceName,
                "core-banking-platform", serviceName, Duration.ofSeconds(60), Duration.ofSeconds(30)));
    }

    private static EvaluateRequest evaluation() {
        return new EvaluateRequest("TXN-" + UUID.randomUUID().toString().substring(0, 8),
                "ACC-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("100.00"), "USD", "GB");
    }

    private ResponseEntity<String> evaluateWith(HttpHeaders headers) {
        return rest.postForEntity("/api/v1/risk/evaluate", new HttpEntity<>(evaluation(), headers), String.class);
    }

    @Test
    void anEvaluationWithNoTokenIsRefused() {
        ResponseEntity<String> response = evaluateWith(new HttpHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("UNAUTHENTICATED");
    }

    @Test
    void anEvaluationFromPaymentServiceIsAccepted() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(
                issuerFor("payment-service", SECRET).mint("risk-aml-service", "risk:evaluate"));

        ResponseEntity<String> response = evaluateWith(headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aTokenSignedWithTheWrongSecretIsRefused() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(issuerFor("attacker", "a-completely-different-secret-long-enough")
                .mint("risk-aml-service", "risk:evaluate"));

        assertThat(evaluateWith(headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aTokenAddressedToAnotherServiceIsRefused() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(issuerFor("payment-service", SECRET).mint("ledger-service", "risk:evaluate"));

        assertThat(evaluateWith(headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void healthProbesStayOpen() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
