package com.bank.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/**
 * A product in the catalogue (FR-ACC-003). Authorized staff define products — currency, interest,
 * fees and limits — without code changes; accounts are opened against a product and inherit its
 * terms.
 */
@Entity
@Table(name = "product")
public class Product {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, unique = true, updatable = false, length = 40)
  private String code;

  @Column(nullable = false, length = 120)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false, length = 20)
  private AccountType accountType;

  @Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode;

  @Column(name = "interest_rate_percent", nullable = false, precision = 9, scale = 4)
  private BigDecimal interestRatePercent;

  @Column(name = "monthly_fee", nullable = false, precision = 19, scale = 4)
  private BigDecimal monthlyFee;

  @Column(name = "min_balance", nullable = false, precision = 19, scale = 4)
  private BigDecimal minBalance;

  @Column(name = "daily_limit", nullable = false, precision = 19, scale = 4)
  private BigDecimal dailyLimit;

  @Column(name = "overdraft_limit", nullable = false, precision = 19, scale = 4)
  private BigDecimal overdraftLimit;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ProductStatus status;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Product() {
    // Required by JPA.
  }

  public Product(
      String code,
      String name,
      AccountType accountType,
      Currency currency,
      BigDecimal interestRatePercent,
      BigDecimal monthlyFee,
      BigDecimal minBalance,
      BigDecimal dailyLimit,
      BigDecimal overdraftLimit) {
    this.id = UUID.randomUUID();
    this.code = code;
    this.name = name;
    this.accountType = accountType;
    this.currencyCode = currency.getCurrencyCode();
    this.interestRatePercent = interestRatePercent;
    this.monthlyFee = monthlyFee;
    this.minBalance = minBalance;
    this.dailyLimit = dailyLimit;
    this.overdraftLimit = overdraftLimit;
    this.status = ProductStatus.ACTIVE;
    this.createdAt = Instant.now();
  }

  public Currency currency() {
    return Currency.getInstance(currencyCode);
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public AccountType getAccountType() {
    return accountType;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  public BigDecimal getInterestRatePercent() {
    return interestRatePercent;
  }

  public BigDecimal getMonthlyFee() {
    return monthlyFee;
  }

  public BigDecimal getMinBalance() {
    return minBalance;
  }

  public BigDecimal getDailyLimit() {
    return dailyLimit;
  }

  public BigDecimal getOverdraftLimit() {
    return overdraftLimit;
  }

  public ProductStatus getStatus() {
    return status;
  }
}
