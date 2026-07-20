package com.bank.ledger.domain;

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
 * A node in the chart of accounts. The running {@code balance} is held in debit-positive terms
 * (sum of debits minus sum of credits); {@link #normalBalance()} flips the sign for credit-normal
 * account types so callers see a natural positive figure. Optimistic locking guards concurrent
 * posts to the same account.
 */
@Entity
@Table(name = "ledger_account")
public class LedgerAccount {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
    private String organizationId;

    @Column(nullable = false, unique = true, updatable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, updatable = false, length = 20)
    private LedgerAccountType accountType;

    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    /** Debit-positive running balance: += debit amount, -= credit amount. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerAccount() {
        // Required by JPA.
    }

    private LedgerAccount(String code, String name, LedgerAccountType type, Currency currency) {
        this.organizationId = com.bank.common.tenant.TenantContext.getOrDefault();
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.accountType = type;
        this.currencyCode = currency.getCurrencyCode();
        this.balance = BigDecimal.ZERO.setScale(currency.getDefaultFractionDigits());
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static LedgerAccount create(String code, String name, LedgerAccountType type, Currency currency) {
        return new LedgerAccount(code, name, type, currency);
    }

    /** Apply one posting leg to the running balance (debit-positive convention). */
    public void apply(Direction direction, Money amount) {
        if (!amount.currency().getCurrencyCode().equals(currencyCode)) {
            throw new IllegalArgumentException(
                    "Posting currency %s does not match account %s currency %s"
                            .formatted(amount.currency().getCurrencyCode(), code, currencyCode));
        }
        BigDecimal delta = amount.amount();
        this.balance = direction == Direction.DEBIT ? balance.add(delta) : balance.subtract(delta);
        this.updatedAt = Instant.now();
    }

    public Currency currency() {
        return Currency.getInstance(currencyCode);
    }

    /** Balance expressed in the account's natural sign (positive when on its normal side). */
    public Money normalBalance() {
        BigDecimal value = accountType.normalSide() == Direction.DEBIT ? balance : balance.negate();
        return Money.of(value, currency());
    }

    /** Raw debit-positive balance. */
    public Money debitPositiveBalance() {
        return Money.of(balance, currency());
    }

    public UUID getId() {
        return id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public LedgerAccountType getAccountType() {
        return accountType;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
