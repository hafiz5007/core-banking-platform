package com.bank.payment.application.port;

import com.bank.payment.application.iso20022.Pacs008Message;
import java.math.BigDecimal;

/**
 * Outbound port to the SWIFT / cross-border network. Submits an ISO 20022 message for an interbank
 * settlement amount in the beneficiary currency and returns the network acknowledgement (a
 * gpi-style reference for end-to-end tracking).
 */
public interface SwiftPort {

  SwiftResult submit(SwiftInstruction instruction);

  record SwiftInstruction(
      String idempotencyKey,
      Pacs008Message message,
      BigDecimal settlementAmount,
      String settlementCurrency) {}

  /**
   * @param accepted whether the network accepted the message
   * @param reference gpi/UETR-style tracking reference when accepted (null otherwise)
   * @param reason rejection reason when not accepted (null otherwise)
   */
  record SwiftResult(boolean accepted, String reference, String reason) {
    public static SwiftResult accepted(String reference) {
      return new SwiftResult(true, reference, null);
    }

    public static SwiftResult rejected(String reason) {
      return new SwiftResult(false, null, reason);
    }
  }
}
