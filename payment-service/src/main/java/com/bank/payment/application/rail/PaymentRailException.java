package com.bank.payment.application.rail;

import java.util.UUID;

/**
 * Thrown by a rail handler when fulfilment fails <em>after</em> the ledger has posted, having
 * already reversed it. Carries the compensating reversal entry id so the orchestrator can record a
 * clean COMPENSATED outcome.
 */
public class PaymentRailException extends RuntimeException {

  private final UUID reversalEntryId;

  public PaymentRailException(UUID reversalEntryId, String reason) {
    super(reason);
    this.reversalEntryId = reversalEntryId;
  }

  public UUID getReversalEntryId() {
    return reversalEntryId;
  }
}
