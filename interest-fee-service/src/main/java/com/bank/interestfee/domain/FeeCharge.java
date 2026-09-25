package com.bank.interestfee.domain;

import com.bank.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/** Audit record of a fee applied to an account and posted to the ledger. */
@Entity
@Table(name = "fee_charge")
public class FeeCharge {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "account_code", nullable = false, updatable = false, length = 40)
  private String accountCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "fee_type", nullable = false, updatable = false, length = 20)
  private FeeType feeType;

  @Column(nullable = false, updatable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
  private String currencyCode;

  @Column(name = "ledger_entry_id", updatable = false)
  private UUID ledgerEntryId;

  @Column(name = "applied_at", nullable = false, updatable = false)
  private Instant appliedAt;

  protected FeeCharge() {
    // Required by JPA.
  }

  public FeeCharge(String accountCode, FeeType feeType, Money amount, UUID ledgerEntryId) {
    this.id = UUID.randomUUID();
    this.accountCode = accountCode;
    this.feeType = feeType;
    this.amount = amount.amount();
    this.currencyCode = amount.currency().getCurrencyCode();
    this.ledgerEntryId = ledgerEntryId;
    this.appliedAt = Instant.now();
  }

  public Money money() {
    return Money.of(amount, Currency.getInstance(currencyCode));
  }

  public UUID getId() {
    return id;
  }

  public String getAccountCode() {
    return accountCode;
  }

  public FeeType getFeeType() {
    return feeType;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  public UUID getLedgerEntryId() {
    return ledgerEntryId;
  }

  public Instant getAppliedAt() {
    return appliedAt;
  }
}
