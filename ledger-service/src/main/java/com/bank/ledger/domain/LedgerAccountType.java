package com.bank.ledger.domain;

/**
 * Classification of a general-ledger account. The normal side determines how a positive
 * debit-minus-credit running balance is interpreted: asset/expense accounts are debit-normal,
 * liability/equity/income accounts are credit-normal.
 */
public enum LedgerAccountType {
    ASSET(Direction.DEBIT),
    EXPENSE(Direction.DEBIT),
    LIABILITY(Direction.CREDIT),
    EQUITY(Direction.CREDIT),
    INCOME(Direction.CREDIT);

    private final Direction normalSide;

    LedgerAccountType(Direction normalSide) {
        this.normalSide = normalSide;
    }

    public Direction normalSide() {
        return normalSide;
    }
}
