package com.bank.ledger.domain;

import com.bank.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/** One leg of a journal entry: a debit or credit of an amount against a single ledger account. */
@Entity
@Table(name = "journal_line")
public class JournalLine {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "ledger_account_id", nullable = false, updatable = false)
  private UUID ledgerAccountId;

  @Column(name = "account_code", nullable = false, updatable = false, length = 40)
  private String accountCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 6)
  private Direction direction;

  @Column(nullable = false, updatable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
  private String currencyCode;

  protected JournalLine() {
    // Required by JPA.
  }

  public JournalLine(UUID ledgerAccountId, String accountCode, Direction direction, Money amount) {
    this.id = UUID.randomUUID();
    this.ledgerAccountId = ledgerAccountId;
    this.accountCode = accountCode;
    this.direction = direction;
    this.amount = amount.amount();
    this.currencyCode = amount.currency().getCurrencyCode();
  }

  public Money money() {
    return Money.of(amount, Currency.getInstance(currencyCode));
  }

  public UUID getId() {
    return id;
  }

  public UUID getLedgerAccountId() {
    return ledgerAccountId;
  }

  public String getAccountCode() {
    return accountCode;
  }

  public Direction getDirection() {
    return direction;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }
}
