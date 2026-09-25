package com.bank.account.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request to debit an account. The debit is posted to the general ledger as a balanced entry
 * (ADR-007); {@code idempotencyKey} makes a retry safe.
 */
public record DebitAccountRequest(
    @NotBlank @Size(max = 80) String idempotencyKey,
    @NotNull @Positive BigDecimal amount,
    @NotNull
        @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
        String currencyCode,
    @Size(max = 280) String narrative) {}
