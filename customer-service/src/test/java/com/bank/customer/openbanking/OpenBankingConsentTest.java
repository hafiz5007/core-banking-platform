package com.bank.customer.openbanking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.common.error.BusinessException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the Open Banking consent lifecycle and SCA requirement. */
class OpenBankingConsentTest {

    private OpenBankingConsent consent(Instant expiresAt) {
        return OpenBankingConsent.request("CR-1", UUID.randomUUID(), "FinTechCo",
                Set.of(ConsentScope.ACCOUNT_INFO, ConsentScope.PAYMENT_INITIATION), expiresAt);
    }

    @Test
    void authoriseRequiresSca() {
        OpenBankingConsent c = consent(Instant.now().plus(30, ChronoUnit.DAYS));
        assertThatThrownBy(() -> c.authorise("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Strong customer authentication");
    }

    @Test
    void authoriseWithScaActivates() {
        OpenBankingConsent c = consent(Instant.now().plus(30, ChronoUnit.DAYS));
        c.authorise("sca-123");
        assertThat(c.isActive()).isTrue();
        assertThat(c.getScaReference()).isEqualTo("sca-123");
    }

    @Test
    void revokeDeactivates() {
        OpenBankingConsent c = consent(Instant.now().plus(30, ChronoUnit.DAYS));
        c.authorise("sca-123");
        c.revoke();
        assertThat(c.isActive()).isFalse();
        assertThat(c.getStatus()).isEqualTo(ConsentStatus.REVOKED);
    }

    @Test
    void expiredConsentIsNotActive() {
        OpenBankingConsent c = consent(Instant.now().minus(1, ChronoUnit.DAYS));
        assertThat(c.isActive()).isFalse();
        assertThat(c.getStatus()).isEqualTo(ConsentStatus.EXPIRED);
    }

    @Test
    void requiresAtLeastOneScope() {
        assertThatThrownBy(() -> OpenBankingConsent.request("CR-2", UUID.randomUUID(), "X",
                Set.of(), Instant.now().plus(1, ChronoUnit.DAYS)))
                .isInstanceOf(BusinessException.class);
    }
}
