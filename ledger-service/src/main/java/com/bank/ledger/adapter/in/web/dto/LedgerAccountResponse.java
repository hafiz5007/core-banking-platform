package com.bank.ledger.adapter.in.web.dto;

import com.bank.ledger.domain.LedgerAccount;
import com.bank.ledger.domain.LedgerAccountType;
import java.util.UUID;

/** Response view of a ledger account, including its natural-sign balance. */
public record LedgerAccountResponse(
        UUID id,
        String organizationId,
        String code,
        String name,
        LedgerAccountType accountType,
        String currencyCode,
        String balance) {

    public static LedgerAccountResponse from(LedgerAccount a) {
        return new LedgerAccountResponse(
                a.getId(), a.getOrganizationId(), a.getCode(), a.getName(), a.getAccountType(),
                a.getCurrencyCode(), a.normalBalance().amount().toPlainString());
    }
}
