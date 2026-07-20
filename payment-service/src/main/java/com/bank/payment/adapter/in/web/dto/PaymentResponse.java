package com.bank.payment.adapter.in.web.dto;

import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentStatus;
import com.bank.payment.domain.PaymentType;
import java.time.Instant;
import java.util.UUID;

/** Response view of a payment, including its lifecycle status and any ledger references. */
public record PaymentResponse(
        UUID id,
        String organizationId,
        String idempotencyKey,
        PaymentType type,
        String debtorAccount,
        String creditorAccount,
        String amount,
        String currencyCode,
        PaymentStatus status,
        UUID ledgerEntryId,
        UUID reversalEntryId,
        String schemeReference,
        String targetCurrencyCode,
        String targetAmount,
        String fxRate,
        String failureReason,
        Instant createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getOrganizationId(), p.getIdempotencyKey(), p.getType(), p.getDebtorAccount(),
                p.getCreditorAccount(), p.getAmount().toPlainString(), p.getCurrencyCode(),
                p.getStatus(), p.getLedgerEntryId(), p.getReversalEntryId(),
                p.getSchemeReference(),
                p.getTargetCurrencyCode(),
                p.getTargetAmount() == null ? null : p.getTargetAmount().toPlainString(),
                p.getFxRate() == null ? null : p.getFxRate().toPlainString(),
                p.getFailureReason(), p.getCreatedAt());
    }
}
