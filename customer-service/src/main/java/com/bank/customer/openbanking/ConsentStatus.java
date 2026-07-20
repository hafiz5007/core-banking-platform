package com.bank.customer.openbanking;

/** Lifecycle of an Open Banking consent (FR-CHN-003). */
public enum ConsentStatus {
    AWAITING_AUTHORISATION,
    ACTIVE,
    REVOKED,
    EXPIRED
}
