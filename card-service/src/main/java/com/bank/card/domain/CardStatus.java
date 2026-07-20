package com.bank.card.domain;

/** Lifecycle states of a debit card (FR-CRD-001). */
public enum CardStatus {
    REQUESTED,
    ACTIVE,
    BLOCKED,
    EXPIRED
}
