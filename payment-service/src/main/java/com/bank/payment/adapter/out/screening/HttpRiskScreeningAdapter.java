package com.bank.payment.adapter.out.screening;

import com.bank.common.money.Money;
import com.bank.common.security.ServiceTokenIssuer;
import com.bank.common.security.web.JwtPropagationInterceptor;
import com.bank.payment.application.port.ScreeningPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real screening adapter: delegates to the risk-aml-service transaction-monitoring engine. A
 * non-ALLOW decision (HOLD/DECLINE) is treated as a screening hold. Enabled with
 * {@code screening.adapter=risk-service}. If the risk service is unreachable the call fails fast and
 * the payment is rejected — payments are never allowed to bypass screening silently.
 */
@Component
@ConditionalOnProperty(name = "screening.adapter", havingValue = "risk-service")
public class HttpRiskScreeningAdapter implements ScreeningPort {

    private static final Logger log = LoggerFactory.getLogger(HttpRiskScreeningAdapter.class);

    private static final String TARGET_AUDIENCE = "risk-aml-service";
    private static final String REQUIRED_SCOPE = "risk:evaluate";

    private final RestClient restClient;

    public HttpRiskScreeningAdapter(RestClient.Builder builder,
                                    @Value("${risk.base-url:http://localhost:8088}") String baseUrl,
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
    public ScreeningResult screen(String creditorAccount, Money amount) {
        EvaluateRequest request = new EvaluateRequest(
                "scr-" + UUID.randomUUID(), creditorAccount,
                amount.amount(), amount.currency().getCurrencyCode(), null);
        EvaluateResponse response = restClient.post()
                .uri("/api/v1/risk/evaluate")
                .body(request)
                .retrieve()
                .body(EvaluateResponse.class);
        if (response == null) {
            throw new IllegalStateException("Risk service returned no decision");
        }
        boolean blocked = !"ALLOW".equals(response.decision());
        if (blocked) {
            log.info("Risk service held payment to {} (rule {})", creditorAccount, response.ruleCode());
        }
        return new ScreeningResult(blocked, response.ruleCode());
    }

    record EvaluateRequest(String transactionRef, String accountRef, BigDecimal amount,
                           String currencyCode, String counterpartyCountry) {
    }

    record EvaluateResponse(String decision, String ruleCode, UUID alertId, UUID caseId) {
    }
}
