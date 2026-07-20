package com.bank.customer.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.customer.domain.RiskRating;
import org.junit.jupiter.api.Test;

class RiskRatingPolicyTest {

    @Test
    void higherRiskCountryIsHigh() {
        assertThat(RiskRatingPolicy.rate("XA", false)).isEqualTo(RiskRating.HIGH);
    }

    @Test
    void missingTaxIdIsMedium() {
        assertThat(RiskRatingPolicy.rate("GB", true)).isEqualTo(RiskRating.MEDIUM);
    }

    @Test
    void otherwiseLow() {
        assertThat(RiskRatingPolicy.rate("GB", false)).isEqualTo(RiskRating.LOW);
    }
}
