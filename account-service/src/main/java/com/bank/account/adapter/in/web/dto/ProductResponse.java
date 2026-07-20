package com.bank.account.adapter.in.web.dto;

import com.bank.account.domain.AccountType;
import com.bank.account.domain.Product;
import com.bank.account.domain.ProductStatus;

/** Response view of a catalogue product. */
public record ProductResponse(
        String code,
        String name,
        AccountType accountType,
        String currencyCode,
        String interestRatePercent,
        String monthlyFee,
        String minBalance,
        String dailyLimit,
        String overdraftLimit,
        ProductStatus status) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getCode(), p.getName(), p.getAccountType(), p.getCurrencyCode(),
                p.getInterestRatePercent().toPlainString(), p.getMonthlyFee().toPlainString(),
                p.getMinBalance().toPlainString(), p.getDailyLimit().toPlainString(),
                p.getOverdraftLimit().toPlainString(), p.getStatus());
    }
}
