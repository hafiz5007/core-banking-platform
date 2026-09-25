package com.bank.account.domain;

/** The kind of entity change recorded in the change log (ADR-001). */
public enum ChangeType {
  CREATE,
  UPDATE,
  DELETE
}
