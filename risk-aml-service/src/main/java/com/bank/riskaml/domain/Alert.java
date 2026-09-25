package com.bank.riskaml.domain;

import com.bank.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An alert raised by a monitoring rule against a transaction. */
@Entity
@Table(name = "aml_alert")
public class Alert {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "transaction_ref", nullable = false, updatable = false, length = 80)
  private String transactionRef;

  @Column(name = "account_ref", nullable = false, updatable = false, length = 40)
  private String accountRef;

  @Column(nullable = false, updatable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
  private String currencyCode;

  @Column(name = "rule_code", nullable = false, updatable = false, length = 40)
  private String ruleCode;

  @Column(name = "case_id", nullable = false, updatable = false)
  private UUID caseId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Alert() {
    // Required by JPA.
  }

  public Alert(
      String transactionRef, String accountRef, Money amount, String ruleCode, UUID caseId) {
    this.id = UUID.randomUUID();
    this.transactionRef = transactionRef;
    this.accountRef = accountRef;
    this.amount = amount.amount();
    this.currencyCode = amount.currency().getCurrencyCode();
    this.ruleCode = ruleCode;
    this.caseId = caseId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getTransactionRef() {
    return transactionRef;
  }

  public String getAccountRef() {
    return accountRef;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  public String getRuleCode() {
    return ruleCode;
  }

  public UUID getCaseId() {
    return caseId;
  }
}
