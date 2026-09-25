package com.bank.payment.application.port;

import com.bank.payment.application.iso20022.Pacs008Message;
import com.bank.payment.domain.PaymentType;

/**
 * Outbound port to domestic clearing schemes (instant / RTGS / ACH). Submits an ISO 20022 message
 * and returns the scheme's acknowledgement. Implementations are rail-aware; in production each rail
 * typically has its own adapter and connectivity.
 */
public interface ClearingPort {

  ClearingResult submit(PaymentType rail, ClearingInstruction instruction);

  record ClearingInstruction(String idempotencyKey, Pacs008Message message) {}

  /**
   * @param accepted whether the scheme accepted the instruction
   * @param schemeReference the scheme's reference when accepted (null otherwise)
   * @param reason rejection reason when not accepted (null otherwise)
   */
  record ClearingResult(boolean accepted, String schemeReference, String reason) {
    public static ClearingResult accepted(String schemeReference) {
      return new ClearingResult(true, schemeReference, null);
    }

    public static ClearingResult rejected(String reason) {
      return new ClearingResult(false, null, reason);
    }
  }
}
