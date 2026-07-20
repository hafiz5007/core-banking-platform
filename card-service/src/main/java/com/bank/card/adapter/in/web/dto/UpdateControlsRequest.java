package com.bank.card.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Request to update a card's usage controls. {@code perTransactionLimit} of 0 means no limit. */
public record UpdateControlsRequest(
        boolean online,
        boolean contactless,
        boolean atm,
        boolean international,
        @NotNull @PositiveOrZero BigDecimal perTransactionLimit,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
        String currencyCode) {
}
