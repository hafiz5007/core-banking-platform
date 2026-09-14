package com.bank.common.error;

/** Stable, machine-readable error codes surfaced to API clients. */
public enum ErrorCode {
    VALIDATION_FAILED,
    RESOURCE_NOT_FOUND,
    BUSINESS_RULE_VIOLATION,
    DUPLICATE_REQUEST,
    /** The caller did not present an acceptable identity (ADR-008 service tokens). */
    UNAUTHENTICATED,
    INTERNAL_ERROR
}
