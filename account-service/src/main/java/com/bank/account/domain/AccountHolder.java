package com.bank.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Links a customer to an account with a role; multiple holders model a joint account. */
@Entity
@Table(name = "account_holder")
public class AccountHolder {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 10)
  private HolderRole role;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AccountHolder() {
    // Required by JPA.
  }

  public AccountHolder(UUID accountId, UUID customerId, HolderRole role) {
    this.id = UUID.randomUUID();
    this.accountId = accountId;
    this.customerId = customerId;
    this.role = role;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public HolderRole getRole() {
    return role;
  }
}
