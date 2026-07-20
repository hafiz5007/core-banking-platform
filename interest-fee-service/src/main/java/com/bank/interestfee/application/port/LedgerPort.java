package com.bank.interestfee.application.port;

import com.bank.common.money.Money;
import java.util.UUID;

/** Outbound port to the ledger service for posting interest and fee movements. */
public interface LedgerPort {

    UUID postTransfer(TransferCommand command);

    record TransferCommand(
            String idempotencyKey,
            String narrative,
            String debtorAccount,
            String creditorAccount,
            Money amount) {
    }
}
