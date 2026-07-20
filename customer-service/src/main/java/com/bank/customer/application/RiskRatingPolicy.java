package com.bank.customer.application;

import com.bank.customer.domain.RiskRating;
import java.util.Set;

/**
 * Simple, deterministic CDD risk-rating policy (FR-CUS-004). Real implementations weigh many
 * factors; this captures the shape and is easy to unit test and later replace with a rules engine.
 */
public final class RiskRatingPolicy {

    // Illustrative higher-risk jurisdictions for the skeleton (ISO-3166 alpha-2).
    private static final Set<String> HIGHER_RISK_COUNTRIES = Set.of("XA", "XB", "XC");

    private RiskRatingPolicy() {
    }

    public static RiskRating rate(String nationality, boolean missingTaxId) {
        if (HIGHER_RISK_COUNTRIES.contains(nationality)) {
            return RiskRating.HIGH;
        }
        if (missingTaxId) {
            return RiskRating.MEDIUM;
        }
        return RiskRating.LOW;
    }
}
