package com.bank.ledger.adapter.in.web.dto;

import com.bank.ledger.domain.LedgerAccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request to create a chart-of-accounts node. */
public record CreateLedgerAccountRequest(
    @NotBlank @Size(max = 40) String code,
    @NotBlank @Size(max = 120) String name,
    @NotNull LedgerAccountType accountType,
    @NotNull
        @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
        String currencyCode) {}
