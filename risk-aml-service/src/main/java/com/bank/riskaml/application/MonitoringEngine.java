package com.bank.riskaml.application;

import com.bank.common.money.Money;
import com.bank.riskaml.domain.Decision;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Real-time transaction monitoring rules (FR-AML-001/002). Deterministic and easily testable; a
 * production system would layer behavioural models and a configurable rules engine on top.
 */
@Component
public class MonitoringEngine {

    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of("XA", "XB", "XC");

    private final BigDecimal largeAmountThreshold;

    public MonitoringEngine(@Value("${aml.large-amount-threshold:10000}") BigDecimal largeAmountThreshold) {
        this.largeAmountThreshold = largeAmountThreshold;
    }

    public Evaluation evaluate(Money amount, String counterpartyCountry) {
        if (counterpartyCountry != null && HIGH_RISK_COUNTRIES.contains(counterpartyCountry)) {
            return new Evaluation(Decision.HOLD, "HIGH_RISK_COUNTRY");
        }
        if (amount.amount().compareTo(largeAmountThreshold) >= 0) {
            return new Evaluation(Decision.HOLD, "LARGE_AMOUNT");
        }
        // Structuring: amounts just under the threshold (>= 90%).
        BigDecimal ninetyPct = largeAmountThreshold.multiply(new BigDecimal("0.90"));
        if (amount.amount().compareTo(ninetyPct) >= 0) {
            return new Evaluation(Decision.ALLOW, "POSSIBLE_STRUCTURING");
        }
        return new Evaluation(Decision.ALLOW, null);
    }

    /**
     * @param decision the monitoring decision
     * @param ruleCode the rule that fired (null when nothing fired)
     */
    public record Evaluation(Decision decision, String ruleCode) {
        public boolean alerted() {
            return ruleCode != null;
        }
    }
}
