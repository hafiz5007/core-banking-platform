package com.bank.interestfee.adapter.in.web.dto;

import com.bank.interestfee.domain.FeeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Request to apply a fee to an account. */
public record ApplyFeeRequest(
    @NotBlank @Size(max = 40) String accountCode,
    @NotNull FeeType feeType,
    @NotNull @Positive BigDecimal amount,
    @NotNull
        @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
        String currencyCode) {}
