package com.bank.account.domain;

/**
 * Authorization rule for an account's holders (FR-ACC-006):
 * SINGLE — one holder; JOINT — all holders must authorize; ANY_ONE — any holder may authorize.
 */
public enum MandateType {
    SINGLE,
    JOINT,
    ANY_ONE
}
