package com.bank.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void appliesCurrencyScaleOnConstruction() {
        Money m = Money.of("10.1", "USD");
        assertThat(m.amount()).isEqualByComparingTo("10.10");
        assertThat(m.amount().scale()).isEqualTo(2);
    }

    @Test
    void roundsHalfEven() {
        // 2.345 at scale 2 with HALF_EVEN rounds to 2.34 (round to even neighbour).
        assertThat(Money.of("2.345", "USD").amount()).isEqualByComparingTo("2.34");
        // 2.355 rounds up to 2.36 (even neighbour).
        assertThat(Money.of("2.355", "USD").amount()).isEqualByComparingTo("2.36");
    }

    @Test
    void addsSameCurrency() {
        Money result = Money.of("10.00", "USD").add(Money.of("5.50", "USD"));
        assertThat(result.amount()).isEqualByComparingTo("15.50");
    }

    @Test
    void subtractsSameCurrency() {
        Money result = Money.of("10.00", "USD").subtract(Money.of("3.25", "USD"));
        assertThat(result.amount()).isEqualByComparingTo("6.75");
    }

    @Test
    void rejectsCrossCurrencyArithmetic() {
        Money usd = Money.of("10.00", "USD");
        Money eur = Money.of("10.00", "EUR");
        assertThatThrownBy(() -> usd.add(eur)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void detectsSignAndZero() {
        assertThat(Money.zero("USD").isZero()).isTrue();
        assertThat(Money.of("-1.00", "USD").isNegative()).isTrue();
        assertThat(Money.of("5.00", "USD").isGreaterThanOrEqual(Money.of("5.00", "USD"))).isTrue();
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> Money.of((BigDecimal) null, java.util.Currency.getInstance("USD")))
                .isInstanceOf(NullPointerException.class);
    }
}
