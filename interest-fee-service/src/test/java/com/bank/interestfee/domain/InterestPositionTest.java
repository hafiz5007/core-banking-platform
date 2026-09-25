package com.bank.interestfee.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.common.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.Test;

/** Unit tests for interest accrual maths and capitalization. */
class InterestPositionTest {

  private static final Currency USD = Currency.getInstance("USD");

  private InterestPosition position(String rate, String principal) {
    return InterestPosition.open(
        "ACC-1",
        USD,
        new BigDecimal(rate),
        Money.of(principal, "USD"),
        DayCountBasis.ACT_365,
        LocalDate.of(2026, 1, 1));
  }

  @Test
  void accruesDailyInterest() {
    // 3.65% on 10,000 over 365 days = 365.00; per day = 1.00. After 10 days: 10.00.
    InterestPosition p = position("3.65", "10000.00");
    p.accrueTo(LocalDate.of(2026, 1, 11));
    assertThat(p.accruedMoney().amount()).isEqualByComparingTo("10.00");
  }

  @Test
  void accrualIsNoOpForPastOrSameDate() {
    InterestPosition p = position("5.00", "10000.00");
    p.accrueTo(LocalDate.of(2025, 12, 31));
    assertThat(p.getAccruedInterest()).isEqualByComparingTo("0");
  }

  @Test
  void capitalizationAddsInterestToPrincipalAndResets() {
    InterestPosition p = position("3.65", "10000.00");
    p.accrueTo(LocalDate.of(2026, 1, 11)); // ~10.00 accrued
    Money capitalized = p.capitalize();
    assertThat(capitalized.amount()).isEqualByComparingTo("10.00");
    assertThat(p.getPrincipal()).isEqualByComparingTo("10010.00");
    assertThat(p.getAccruedInterest()).isEqualByComparingTo("0");
  }
}
