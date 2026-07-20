package com.bank.customer.domain;

/** Customer due-diligence risk rating, which drives KYC refresh cadence (see FR-CUS-004). */
public enum RiskRating {
    LOW,
    MEDIUM,
    HIGH
}
