package com.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.common.error.BusinessException;
import com.bank.common.money.CurrencyMismatchException;
import com.bank.common.money.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests for account limits and the product-based factory. */
class AccountTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void simpleAccountHasSingleMandateAndZeroLimits() {
        Account a = Account.open("DEFAULT", UUID.randomUUID(), AccountType.SAVINGS, USD, "000000000001");
        assertThat(a.getOrganizationId()).isEqualTo("DEFAULT");
        assertThat(a.getMandateType()).isEqualTo(MandateType.SINGLE);
        assertThat(a.getOverdraftLimit()).isEqualByComparingTo("0.00");
        assertThat(a.availableToSpend().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void productAccountInheritsLimitsAndOverdraftExtendsSpendable() {
        Product product = new Product("CUR-STD", "Standard Current", AccountType.CURRENT, USD,
                new BigDecimal("0.50"), new BigDecimal("5.00"), new BigDecimal("0.00"),
                new BigDecimal("5000.00"), new BigDecimal("100.00"));

        Account a = Account.openFromProduct("ORG-1", UUID.randomUUID(), "000000000002", product, MandateType.JOINT);

        assertThat(a.getProductCode()).isEqualTo("CUR-STD");
        assertThat(a.getMandateType()).isEqualTo(MandateType.JOINT);
        assertThat(a.getOverdraftLimit()).isEqualByComparingTo("100.00");
        // Zero balance + 100 overdraft = 100 spendable.
        assertThat(a.availableToSpend().amount()).isEqualByComparingTo("100.00");
        assertThat(a.canWithdraw(Money.of("100.00", "USD"))).isTrue();
        assertThat(a.canWithdraw(Money.of("100.01", "USD"))).isFalse();
    }

    private static Account overdraftAccount() {
        Product product = new Product("CUR-STD", "Standard Current", AccountType.CURRENT, USD,
                new BigDecimal("0.50"), new BigDecimal("5.00"), new BigDecimal("0.00"),
                new BigDecimal("5000.00"), new BigDecimal("100.00"));
        return Account.openFromProduct("ORG-1", UUID.randomUUID(), "000000000003", product, MandateType.SINGLE);
    }

    @Test
    void debitReducesTheBalance() {
        Account a = overdraftAccount();

        a.debit(Money.of("40.00", "USD"));

        assertThat(a.balance().amount()).isEqualByComparingTo("-40.00");
        // 100 overdraft less the 40 drawn.
        assertThat(a.availableToSpend().amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void debitMayDrawTheFullOverdraftButNoMore() {
        Account a = overdraftAccount();

        a.debit(Money.of("100.00", "USD"));

        assertThat(a.balance().amount()).isEqualByComparingTo("-100.00");
        assertThatThrownBy(() -> a.debit(Money.of("0.01", "USD")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void debitBeyondAvailableFundsIsRejected() {
        Account a = overdraftAccount();

        assertThatThrownBy(() -> a.debit(Money.of("100.01", "USD")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient funds");
        assertThat(a.balance().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void debitInAnotherCurrencyIsRejected() {
        Account a = overdraftAccount();

        assertThatThrownBy(() -> a.debit(Money.of("10.00", "EUR")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void debitMustBePositive() {
        Account a = overdraftAccount();

        assertThatThrownBy(() -> a.debit(Money.of("0.00", "USD")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be positive");
        assertThatThrownBy(() -> a.debit(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void onlyAnActiveAccountMayBeDebited() {
        Account a = overdraftAccount();
        // Account has no lifecycle transitions yet, so drive the status directly to prove the guard.
        ReflectionTestUtils.setField(a, "status", AccountStatus.CLOSED);

        assertThatThrownBy(() -> a.debit(Money.of("10.00", "USD")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be debited");
    }
}
