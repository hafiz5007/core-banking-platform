package com.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.common.money.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}
