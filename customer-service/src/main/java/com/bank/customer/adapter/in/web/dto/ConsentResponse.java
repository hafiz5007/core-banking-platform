package com.bank.customer.adapter.in.web.dto;

import com.bank.customer.domain.Consent;
import com.bank.customer.domain.ConsentType;
import java.time.Instant;
import java.util.UUID;

/** Response view of a recorded consent. */
public record ConsentResponse(
        UUID id,
        UUID customerId,
        ConsentType consentType,
        boolean granted,
        String channel,
        Instant recordedAt) {

    public static ConsentResponse from(Consent c) {
        return new ConsentResponse(
                c.getId(), c.getCustomerId(), c.getConsentType(),
                c.isGranted(), c.getChannel(), c.getRecordedAt());
    }
}
