package com.bank.payment.adapter.in.web.dto;

import com.bank.payment.domain.PaymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request to initiate a payment.
 *
 * @param type rail to use; defaults to INTRABANK when omitted
 * @param debtorAccount ledger account code to debit
 * @param creditorAccount ledger account code to credit
 */
public record InitiatePaymentRequest(
    @NotBlank @Size(max = 80) String idempotencyKey,
    PaymentType type,
    @NotBlank @Size(max = 40) String debtorAccount,
    @NotBlank @Size(max = 40) String creditorAccount,
    @NotNull @Positive BigDecimal amount,
    @NotNull
        @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
        String currencyCode,
    @Size(max = 280) String narrative,
    @Pattern(regexp = "^[A-Z]{3}$", message = "targetCurrencyCode must be a 3-letter ISO-4217 code")
        String targetCurrencyCode) {

  public PaymentType typeOrDefault() {
    return type == null ? PaymentType.INTRABANK : type;
  }
}
