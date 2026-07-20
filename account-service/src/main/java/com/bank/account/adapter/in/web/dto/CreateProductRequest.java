package com.bank.account.adapter.in.web.dto;

import com.bank.account.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Request to define a catalogue product. */
public record CreateProductRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull AccountType accountType,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
        String currencyCode,
        @NotNull @PositiveOrZero BigDecimal interestRatePercent,
        @NotNull @PositiveOrZero BigDecimal monthlyFee,
        @NotNull @PositiveOrZero BigDecimal minBalance,
        @NotNull @PositiveOrZero BigDecimal dailyLimit,
        @NotNull @PositiveOrZero BigDecimal overdraftLimit) {
}
