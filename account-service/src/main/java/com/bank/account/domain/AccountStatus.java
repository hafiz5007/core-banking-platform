package com.bank.account.domain;

/** Lifecycle states an account may occupy (see FR-ACC-002). */
public enum AccountStatus {
    ACTIVE,
    DORMANT,
    BLOCKED,
    FROZEN,
    CLOSED
}
