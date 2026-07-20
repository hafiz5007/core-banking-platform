package com.bank.payment.domain;

/**
 * Supported payment rails. Sprint 4 implements INTRABANK; the remaining values are placeholders the
 * routing engine will dispatch to domestic and overseas adapters in later sprints.
 */
public enum PaymentType {
    INTRABANK,
    DOMESTIC_INSTANT,
    RTGS,
    ACH,
    CROSS_BORDER
}
