package com.bank.card.domain;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.money.Money;
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
 * Debit card aggregate. PCI scope is minimised: only a card token and the last four digits are
 * stored — never the full PAN. The card tracks an available balance against which authorizations
 * place holds; settlement converts a hold to a debit, reversal releases it.
 */
@Entity
@Table(name = "card")
public class Card {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
  private String organizationId;

  @Column(name = "card_token", nullable = false, unique = true, updatable = false, length = 60)
  private String cardToken;

  @Column(name = "last_four", nullable = false, updatable = false, length = 4)
  private String lastFour;

  @Column(name = "account_code", nullable = false, updatable = false, length = 40)
  private String accountCode;

  @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
  private String currencyCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private CardStatus status;

  @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
  private BigDecimal availableBalance;

  @Column(name = "online_enabled", nullable = false)
  private boolean onlineEnabled;

  @Column(name = "contactless_enabled", nullable = false)
  private boolean contactlessEnabled;

  @Column(name = "atm_enabled", nullable = false)
  private boolean atmEnabled;

  @Column(name = "international_enabled", nullable = false)
  private boolean internationalEnabled;

  /** Per-transaction limit; zero means no limit. */
  @Column(name = "per_transaction_limit", nullable = false, precision = 19, scale = 4)
  private BigDecimal perTransactionLimit;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Card() {
    // Required by JPA.
  }

  private Card(String cardToken, String lastFour, String accountCode, Money openingAvailable) {
    Instant now = Instant.now();
    this.id = UUID.randomUUID();
    this.organizationId = com.bank.common.tenant.TenantContext.getOrDefault();
    this.cardToken = cardToken;
    this.lastFour = lastFour;
    this.accountCode = accountCode;
    this.currencyCode = openingAvailable.currency().getCurrencyCode();
    this.availableBalance = openingAvailable.amount();
    this.status = CardStatus.ACTIVE;
    // Sensible defaults: domestic channels on, international off, no per-transaction limit.
    this.onlineEnabled = true;
    this.contactlessEnabled = true;
    this.atmEnabled = true;
    this.internationalEnabled = false;
    this.perTransactionLimit =
        BigDecimal.ZERO.setScale(openingAvailable.currency().getDefaultFractionDigits());
    this.createdAt = now;
    this.updatedAt = now;
  }

  public static Card issue(
      String cardToken, String lastFour, String accountCode, Money openingAvailable) {
    return new Card(cardToken, lastFour, accountCode, openingAvailable);
  }

  public void block() {
    this.status = CardStatus.BLOCKED;
    touch();
  }

  public void unblock() {
    if (status == CardStatus.BLOCKED) {
      this.status = CardStatus.ACTIVE;
      touch();
    }
  }

  /** Update customer usage controls (FR-CRD-004). */
  public void updateControls(
      boolean online,
      boolean contactless,
      boolean atm,
      boolean international,
      Money perTransactionLimit) {
    this.onlineEnabled = online;
    this.contactlessEnabled = contactless;
    this.atmEnabled = atm;
    this.internationalEnabled = international;
    this.perTransactionLimit = perTransactionLimit.amount();
    touch();
  }

  /**
   * Place a hold for an authorization, enforcing status, usage controls and available funds. Throws
   * {@link BusinessException} on any breach (the caller maps that to a DECLINE).
   */
  public void placeHold(Money amount, CardChannel channel, boolean international) {
    if (status != CardStatus.ACTIVE) {
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION, "Card is not active: " + status);
    }
    if (!channelEnabled(channel)) {
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION, "Channel disabled: " + channel);
    }
    if (international && !internationalEnabled) {
      throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "International use disabled");
    }
    if (perTransactionLimit.signum() > 0 && amount.amount().compareTo(perTransactionLimit) > 0) {
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION, "Exceeds per-transaction limit");
    }
    if (available().amount().compareTo(amount.amount()) < 0) {
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION, "Insufficient available balance");
    }
    this.availableBalance = availableBalance.subtract(amount.amount());
    touch();
  }

  private boolean channelEnabled(CardChannel channel) {
    return switch (channel) {
      case ONLINE -> onlineEnabled;
      case CONTACTLESS -> contactlessEnabled;
      case ATM -> atmEnabled;
      case POS -> true;
    };
  }

  /** Release a previously placed hold (authorization reversal). */
  public void releaseHold(Money amount) {
    this.availableBalance = availableBalance.add(amount.amount());
    touch();
  }

  public Money available() {
    return Money.of(availableBalance, Currency.getInstance(currencyCode));
  }

  public Currency currency() {
    return Currency.getInstance(currencyCode);
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public String getCardToken() {
    return cardToken;
  }

  public String getLastFour() {
    return lastFour;
  }

  public String getAccountCode() {
    return accountCode;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  public CardStatus getStatus() {
    return status;
  }

  public BigDecimal getAvailableBalance() {
    return availableBalance;
  }

  public boolean isOnlineEnabled() {
    return onlineEnabled;
  }

  public boolean isContactlessEnabled() {
    return contactlessEnabled;
  }

  public boolean isAtmEnabled() {
    return atmEnabled;
  }

  public boolean isInternationalEnabled() {
    return internationalEnabled;
  }

  public BigDecimal getPerTransactionLimit() {
    return perTransactionLimit;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
