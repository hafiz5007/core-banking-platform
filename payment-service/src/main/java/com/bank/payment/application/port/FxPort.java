package com.bank.payment.application.port;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Outbound port to an FX rate provider. Returns a mid-market rate plus the bank's spread; the
 * effective rate applied to a customer conversion is the mid-market rate reduced by the spread.
 */
public interface FxPort {

  FxQuote quote(String sourceCurrency, String targetCurrency);

  /**
   * @param sourceCurrency source ISO-4217 currency
   * @param targetCurrency target ISO-4217 currency
   * @param rate mid-market rate (target units per 1 source unit)
   * @param spread fractional spread the bank applies (e.g. 0.005 = 50 bps)
   */
  record FxQuote(String sourceCurrency, String targetCurrency, BigDecimal rate, BigDecimal spread) {

    /** Rate actually applied to the customer: mid-market reduced by the spread. */
    public BigDecimal effectiveRate() {
      return rate.multiply(BigDecimal.ONE.subtract(spread)).setScale(8, RoundingMode.HALF_EVEN);
    }
  }
}
