package com.bank.card.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Request to issue a debit card linked to an account. */
public record IssueCardRequest(
        @NotBlank String accountCode,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
        String currencyCode,
        @NotNull @PositiveOrZero BigDecimal openingAvailable) {
}
