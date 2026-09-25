package com.bank.payment.domain.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.common.money.Money;
import org.junit.jupiter.api.Test;

/** Unit tests for direct-debit mandate collection rules. */
class DirectDebitMandateTest {

  private DirectDebitMandate mandate() {
    return DirectDebitMandate.create("MNDT-1", "1000", "2000", Money.of("100.00", "USD"));
  }

  @Test
  void allowsCollectionWithinCeiling() {
    assertThat(mandate().canCollect(Money.of("100.00", "USD"))).isTrue();
    assertThat(mandate().canCollect(Money.of("50.00", "USD"))).isTrue();
  }

  @Test
  void rejectsCollectionAboveCeiling() {
    assertThat(mandate().canCollect(Money.of("100.01", "USD"))).isFalse();
  }

  @Test
  void rejectsWrongCurrency() {
    assertThat(mandate().canCollect(Money.of("10.00", "EUR"))).isFalse();
  }

  @Test
  void cancelledMandateRejectsCollection() {
    DirectDebitMandate m = mandate();
    m.cancel();
    assertThat(m.canCollect(Money.of("10.00", "USD"))).isFalse();
  }
}
