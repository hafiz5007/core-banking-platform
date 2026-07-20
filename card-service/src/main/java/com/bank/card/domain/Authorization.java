package com.bank.card.domain;

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

/** A card authorization request and its outcome. */
@Entity
@Table(name = "card_authorization")
public class Authorization {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, updatable = false, length = 120)
    private String merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuthorizationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Authorization() {
        // Required by JPA.
    }

    public Authorization(UUID cardId, Money amount, String merchant, AuthorizationStatus status) {
        this.id = UUID.randomUUID();
        this.cardId = cardId;
        this.amount = amount.amount();
        this.currencyCode = amount.currency().getCurrencyCode();
        this.merchant = merchant;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public void settle() {
        this.status = AuthorizationStatus.SETTLED;
    }

    public void reverse() {
        this.status = AuthorizationStatus.REVERSED;
    }

    public Money money() {
        return Money.of(amount, Currency.getInstance(currencyCode));
    }

    public UUID getId() {
        return id;
    }

    public UUID getCardId() {
        return cardId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getMerchant() {
        return merchant;
    }

    public AuthorizationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
