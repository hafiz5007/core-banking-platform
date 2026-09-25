package com.bank.card.domain;

/**
 * Channel a card transaction is presented through; used for customer usage controls (FR-CRD-004).
 */
public enum CardChannel {
  POS,
  ONLINE,
  ATM,
  CONTACTLESS
}
