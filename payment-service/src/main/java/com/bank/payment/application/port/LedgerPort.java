package com.bank.payment.application.port;

import com.bank.common.money.Money;
import java.util.UUID;

/**
 * Outbound port to the ledger service. The payment orchestrator depends on this interface, not on
 * HTTP details, so the transport (or a test fake) can be swapped freely.
 */
public interface LedgerPort {

  /**
   * Post a balanced intrabank transfer: debit the debtor account, credit the creditor account.
   *
   * @return the id of the created journal entry, used later for compensation
   */
  UUID postTransfer(TransferCommand command);

  /** Reverse a previously posted entry (compensation). */
  UUID reverse(UUID ledgerEntryId, String idempotencyKey);

  record TransferCommand(
      String idempotencyKey,
      String narrative,
      String debtorAccount,
      String creditorAccount,
      Money amount) {}
}
