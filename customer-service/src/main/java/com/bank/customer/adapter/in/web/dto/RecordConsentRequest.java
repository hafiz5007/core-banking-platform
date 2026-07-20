package com.bank.customer.adapter.in.web.dto;

import com.bank.customer.domain.ConsentType;
import jakarta.validation.constraints.NotNull;

/** Request to record (grant or withdraw) a consent (FR-CUS-006). */
public record RecordConsentRequest(
        @NotNull ConsentType consentType,
        boolean granted,
        String channel) {
}
