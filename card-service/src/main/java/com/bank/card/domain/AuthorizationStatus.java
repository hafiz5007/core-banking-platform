package com.bank.card.domain;

/** Outcome/lifecycle of a card authorization. */
public enum AuthorizationStatus {
  APPROVED,
  DECLINED,
  SETTLED,
  REVERSED
}
