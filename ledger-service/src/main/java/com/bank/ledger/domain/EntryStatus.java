package com.bank.ledger.domain;

/** Lifecycle of a journal entry. Entries are immutable; a mistake is corrected by a reversal. */
public enum EntryStatus {
    POSTED,
    REVERSED
}
