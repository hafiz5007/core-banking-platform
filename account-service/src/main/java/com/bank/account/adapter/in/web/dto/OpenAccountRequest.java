package com.bank.account.adapter.in.web.dto;

import com.bank.account.domain.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Request to open an account.
 *
 * @param customerId   the owning customer (CIF)
 * @param accountType  product family
 * @param currencyCode ISO-4217 currency code (e.g. "USD")
 */
public record OpenAccountRequest(
        @NotNull UUID customerId,
        @NotNull AccountType accountType,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
        String currencyCode) {
}
