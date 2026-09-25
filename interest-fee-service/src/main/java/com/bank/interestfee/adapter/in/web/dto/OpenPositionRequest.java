package com.bank.interestfee.adapter.in.web.dto;

import com.bank.interestfee.domain.DayCountBasis;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Request to open an interest-bearing position for an account. */
public record OpenPositionRequest(
    @NotBlank @Size(max = 40) String accountCode,
    @NotNull
        @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
        String currencyCode,
    @NotNull @PositiveOrZero BigDecimal annualRatePercent,
    @NotNull @PositiveOrZero BigDecimal principal,
    DayCountBasis dayCount) {

  public DayCountBasis dayCountOrDefault() {
    return dayCount == null ? DayCountBasis.ACT_365 : dayCount;
  }
}
