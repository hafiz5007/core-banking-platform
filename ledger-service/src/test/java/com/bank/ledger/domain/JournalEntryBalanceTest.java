package com.bank.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.common.error.BusinessException;
import com.bank.common.money.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the double-entry balanced invariant — no database required. */
class JournalEntryBalanceTest {

    private JournalLine line(Direction direction, String amount) {
        return new JournalLine(UUID.randomUUID(), "ACC-" + direction, direction, Money.of(amount, "USD"));
    }

    @Test
    void acceptsBalancedEntry() {
        List<JournalLine> lines = List.of(line(Direction.DEBIT, "100.00"), line(Direction.CREDIT, "100.00"));
        // Should not throw.
        JournalEntry.validateBalanced(lines);
    }

    @Test
    void rejectsUnbalancedEntry() {
        List<JournalLine> lines = List.of(line(Direction.DEBIT, "100.00"), line(Direction.CREDIT, "99.99"));
        assertThatThrownBy(() -> JournalEntry.validateBalanced(lines))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unbalanced");
    }

    @Test
    void rejectsSingleLineEntry() {
        assertThatThrownBy(() -> JournalEntry.validateBalanced(List.of(line(Direction.DEBIT, "100.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least two lines");
    }

    @Test
    void rejectsMixedCurrencies() {
        JournalLine usd = new JournalLine(UUID.randomUUID(), "A", Direction.DEBIT, Money.of("10.00", "USD"));
        JournalLine eur = new JournalLine(UUID.randomUUID(), "B", Direction.CREDIT, Money.of("10.00", "EUR"));
        assertThatThrownBy(() -> JournalEntry.validateBalanced(List.of(usd, eur)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("one currency");
    }

    @Test
    void postBuildsBalancedEntry() {
        JournalEntry entry = JournalEntry.post("key-1", "Test",
                java.time.LocalDate.now(),
                List.of(line(Direction.DEBIT, "50.00"), line(Direction.CREDIT, "50.00")));
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertThat(entry.getLines()).hasSize(2);
    }
}
