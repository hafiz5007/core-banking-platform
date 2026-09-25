package com.bank.customer.domain;

/** Onboarding/lifecycle state of a customer (see FR-CUS). */
public enum CustomerStatus {
  /** Created but not yet cleared for activation (KYC pending or referred). */
  PENDING,
  /** Fully onboarded: KYC verified and no screening hit. */
  ACTIVE,
  /** Blocked pending investigation (e.g. a sanctions/PEP match). */
  BLOCKED,
  /** Offboarded / closed. */
  CLOSED
}
