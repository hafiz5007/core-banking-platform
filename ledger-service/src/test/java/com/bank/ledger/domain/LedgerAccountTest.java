package com.bank.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.common.money.Money;
import java.util.Currency;
import org.junit.jupiter.api.Test;

/** Unit tests for balance maths and normal-side interpretation. */
class LedgerAccountTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void debitNormalAccountShowsPositiveAfterDebit() {
        LedgerAccount asset = LedgerAccount.create("1000", "Cash", LedgerAccountType.ASSET, USD);
        asset.apply(Direction.DEBIT, Money.of("100.00", "USD"));
        assertThat(asset.normalBalance().amount()).isEqualByComparingTo("100.00");
        assertThat(asset.debitPositiveBalance().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void creditNormalAccountShowsPositiveAfterCredit() {
        LedgerAccount liability =
                LedgerAccount.create("2000", "Customer Deposits", LedgerAccountType.LIABILITY, USD);
        liability.apply(Direction.CREDIT, Money.of("100.00", "USD"));
        // Debit-positive balance is negative, but the natural (normal-side) balance is positive.
        assertThat(liability.debitPositiveBalance().amount()).isEqualByComparingTo("-100.00");
        assertThat(liability.normalBalance().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void debitThenCreditNets() {
        LedgerAccount asset = LedgerAccount.create("1000", "Cash", LedgerAccountType.ASSET, USD);
        asset.apply(Direction.DEBIT, Money.of("100.00", "USD"));
        asset.apply(Direction.CREDIT, Money.of("30.00", "USD"));
        assertThat(asset.normalBalance().amount()).isEqualByComparingTo("70.00");
    }
}
