package com.bank.payment.domain.directory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A proxy (phone/email) that resolves to an account for P2P payments (FR-PAY-010). */
@Entity
@Table(name = "payment_alias")
public class PaymentAlias {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, unique = true, updatable = false, length = 120)
  private String alias;

  @Enumerated(EnumType.STRING)
  @Column(name = "alias_type", nullable = false, updatable = false, length = 10)
  private AliasType aliasType;

  @Column(name = "account_code", nullable = false, length = 40)
  private String accountCode;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PaymentAlias() {
    // Required by JPA.
  }

  public PaymentAlias(String alias, AliasType aliasType, String accountCode) {
    this.id = UUID.randomUUID();
    this.alias = alias;
    this.aliasType = aliasType;
    this.accountCode = accountCode;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getAlias() {
    return alias;
  }

  public AliasType getAliasType() {
    return aliasType;
  }

  public String getAccountCode() {
    return accountCode;
  }
}
