package com.bank.card.application.port;

import com.bank.common.money.Money;
import java.util.UUID;

/** Outbound port to the ledger service for posting card settlement movements. */
public interface LedgerPort {

  UUID postTransfer(TransferCommand command);

  record TransferCommand(
      String idempotencyKey,
      String narrative,
      String debtorAccount,
      String creditorAccount,
      Money amount) {}
}
