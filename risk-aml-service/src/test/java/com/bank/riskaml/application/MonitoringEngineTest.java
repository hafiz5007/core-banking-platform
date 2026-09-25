package com.bank.riskaml.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.common.money.Money;
import com.bank.riskaml.application.MonitoringEngine.Evaluation;
import com.bank.riskaml.domain.Decision;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for the monitoring rules. */
class MonitoringEngineTest {

  private final MonitoringEngine engine = new MonitoringEngine(new BigDecimal("10000"));

  @Test
  void largeAmountHolds() {
    Evaluation e = engine.evaluate(Money.of("12000.00", "USD"), null);
    assertThat(e.decision()).isEqualTo(Decision.HOLD);
    assertThat(e.ruleCode()).isEqualTo("LARGE_AMOUNT");
  }

  @Test
  void highRiskCountryHolds() {
    Evaluation e = engine.evaluate(Money.of("100.00", "USD"), "XA");
    assertThat(e.decision()).isEqualTo(Decision.HOLD);
    assertThat(e.ruleCode()).isEqualTo("HIGH_RISK_COUNTRY");
  }

  @Test
  void structuringAlertsButAllows() {
    Evaluation e = engine.evaluate(Money.of("9500.00", "USD"), "GB");
    assertThat(e.decision()).isEqualTo(Decision.ALLOW);
    assertThat(e.ruleCode()).isEqualTo("POSSIBLE_STRUCTURING");
  }

  @Test
  void normalTransactionAllowsWithoutAlert() {
    Evaluation e = engine.evaluate(Money.of("100.00", "USD"), "GB");
    assertThat(e.decision()).isEqualTo(Decision.ALLOW);
    assertThat(e.alerted()).isFalse();
  }
}
