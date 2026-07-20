package com.bank.payment.domain.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.common.money.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unit tests for standing-order recurrence and lifecycle. */
class StandingOrderTest {

    private StandingOrder monthly(LocalDate start, LocalDate end) {
        return StandingOrder.create("1000", "2000", Money.of("50.00", "USD"),
                "Rent", Frequency.MONTHLY, start, end);
    }

    @Test
    void isDueOnOrAfterNextRunDate() {
        StandingOrder so = monthly(LocalDate.of(2026, 1, 1), null);
        assertThat(so.isDue(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(so.isDue(LocalDate.of(2025, 12, 31))).isFalse();
    }

    @Test
    void advanceMovesToNextPeriod() {
        StandingOrder so = monthly(LocalDate.of(2026, 1, 1), null);
        so.advance();
        assertThat(so.getNextRunDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(so.getStatus()).isEqualTo(StandingOrderStatus.ACTIVE);
    }

    @Test
    void completesWhenNextRunPassesEndDate() {
        StandingOrder so = monthly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));
        so.advance(); // next would be 2026-02-01, past the end date
        assertThat(so.getStatus()).isEqualTo(StandingOrderStatus.COMPLETED);
    }

    @Test
    void cancelStopsItBeingDue() {
        StandingOrder so = monthly(LocalDate.of(2026, 1, 1), null);
        so.cancel();
        assertThat(so.isDue(LocalDate.of(2026, 6, 1))).isFalse();
    }
}
