package com.bank.interestfee.domain;

import java.math.BigDecimal;

/** Day-count conventions for interest accrual. */
public enum DayCountBasis {
  ACT_365(new BigDecimal("365")),
  ACT_360(new BigDecimal("360"));

  private final BigDecimal denominator;

  DayCountBasis(BigDecimal denominator) {
    this.denominator = denominator;
  }

  public BigDecimal denominator() {
    return denominator;
  }
}
