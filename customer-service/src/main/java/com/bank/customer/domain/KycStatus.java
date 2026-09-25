package com.bank.customer.domain;

/** Outcome of identity/document verification (see FR-CUS-002). */
public enum KycStatus {
  NOT_STARTED,
  VERIFIED,
  REFERRED,
  FAILED
}
