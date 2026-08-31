package com.bank.account.application.port;

import com.bank.common.money.Money;

/**
 * Outbound port for posting a customer-account debit into the general ledger.
 *
 * <p>A debit of a customer account is one half of a balanced entry: the customer's deposit-liability
 * account is debited and a settlement account is credited. Implementations are config-switched via
 * {@code ledger.posting} — a no-op by default, gRPC to ledger-service in a wired deployment
 * (ADR-007).
 */
public interface LedgerPostingPort {

    /**
     * Post the debit. Idempotent on {@code idempotencyKey}: replaying the same key must not post a
     * second entry.
     *
     * @return the ledger's entry id, or {@code null} when no ledger is wired
     */
    String postDebit(String accountCode, Money amount, String narrative, String idempotencyKey);
}
