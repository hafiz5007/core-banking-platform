package com.bank.common.money;

import java.util.Currency;

/** Thrown when arithmetic is attempted across two different currencies. */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency left, Currency right) {
        super("Cannot operate across currencies: %s vs %s"
                .formatted(left.getCurrencyCode(), right.getCurrencyCode()));
    }
}
