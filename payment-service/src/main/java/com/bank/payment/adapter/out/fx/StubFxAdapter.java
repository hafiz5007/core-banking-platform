package com.bank.payment.adapter.out.fx;

import com.bank.payment.application.port.FxPort;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Stub FX rate provider for local development and tests. Uses a small fixed rate table and a flat
 * spread; same-currency pairs convert 1:1 with no spread. Replace with a live rate feed adapter.
 */
@Component
public class StubFxAdapter implements FxPort {

  private static final BigDecimal DEFAULT_SPREAD = new BigDecimal("0.0050");
  private static final Map<String, BigDecimal> RATES =
      Map.of(
          "USD>EUR", new BigDecimal("0.92"),
          "EUR>USD", new BigDecimal("1.087"),
          "USD>GBP", new BigDecimal("0.79"),
          "GBP>USD", new BigDecimal("1.266"),
          "EUR>GBP", new BigDecimal("0.86"),
          "GBP>EUR", new BigDecimal("1.163"));

  @Override
  public FxQuote quote(String sourceCurrency, String targetCurrency) {
    if (sourceCurrency.equals(targetCurrency)) {
      return new FxQuote(sourceCurrency, targetCurrency, BigDecimal.ONE, BigDecimal.ZERO);
    }
    BigDecimal rate =
        RATES.getOrDefault(sourceCurrency + ">" + targetCurrency, new BigDecimal("1.10"));
    return new FxQuote(sourceCurrency, targetCurrency, rate, DEFAULT_SPREAD);
  }
}
