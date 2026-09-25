package com.bank.ledger.adapter.in.web.dto;

import com.bank.ledger.application.LedgerService.TrialBalance;

/** Response view of the trial balance / reconciliation proof. */
public record TrialBalanceResponse(String totalDebits, String totalCredits, boolean balanced) {

  public static TrialBalanceResponse from(TrialBalance tb) {
    return new TrialBalanceResponse(
        tb.totalDebits().toPlainString(), tb.totalCredits().toPlainString(), tb.balanced());
  }
}
