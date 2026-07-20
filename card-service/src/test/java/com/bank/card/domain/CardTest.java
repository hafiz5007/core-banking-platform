package com.bank.card.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.common.error.BusinessException;
import com.bank.common.money.Money;
import org.junit.jupiter.api.Test;

/** Unit tests for card holds, usage controls and authorization guards. */
class CardTest {

    private Card card(String available) {
        return Card.issue("tok_1", "1234", "ACC-1", Money.of(available, "USD"));
    }

    @Test
    void placeHoldReducesAvailable() {
        Card c = card("100.00");
        c.placeHold(Money.of("30.00", "USD"), CardChannel.POS, false);
        assertThat(c.available().amount()).isEqualByComparingTo("70.00");
    }

    @Test
    void releaseHoldRestoresAvailable() {
        Card c = card("100.00");
        c.placeHold(Money.of("30.00", "USD"), CardChannel.POS, false);
        c.releaseHold(Money.of("30.00", "USD"));
        assertThat(c.available().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void insufficientFundsRejected() {
        Card c = card("10.00");
        assertThatThrownBy(() -> c.placeHold(Money.of("20.00", "USD"), CardChannel.POS, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    void blockedCardCannotAuthorize() {
        Card c = card("100.00");
        c.block();
        assertThatThrownBy(() -> c.placeHold(Money.of("10.00", "USD"), CardChannel.POS, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void internationalDisabledByDefault() {
        Card c = card("100.00");
        assertThatThrownBy(() -> c.placeHold(Money.of("10.00", "USD"), CardChannel.POS, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("International");
    }

    @Test
    void controlsCanEnableInternationalAndCapPerTransaction() {
        Card c = card("1000.00");
        c.updateControls(true, true, true, true, Money.of("100.00", "USD"));
        // International now allowed, but a 150 transaction exceeds the 100 limit.
        assertThatThrownBy(() -> c.placeHold(Money.of("150.00", "USD"), CardChannel.ONLINE, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("per-transaction limit");
        // A 100 international transaction is allowed.
        c.placeHold(Money.of("100.00", "USD"), CardChannel.ONLINE, true);
        assertThat(c.available().amount()).isEqualByComparingTo("900.00");
    }

    @Test
    void disabledChannelRejected() {
        Card c = card("100.00");
        c.updateControls(false, true, true, false, Money.of("0.00", "USD"));
        assertThatThrownBy(() -> c.placeHold(Money.of("10.00", "USD"), CardChannel.ONLINE, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Channel disabled");
    }
}
