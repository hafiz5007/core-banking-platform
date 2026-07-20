package com.bank.account.domain;

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
 * Account aggregate root. Balance is stored as an exact {@code NUMERIC} amount plus an ISO-4217
 * currency code — never a floating-point column — and exposed to the rest of the system as a
 * {@link Money} value object. Optimistic locking ({@link Version}) guards concurrent updates.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true, updatable = false, length = 34)
    private String accountNumber;

    @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
    private String organizationId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, updatable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "balance_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAmount;

    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    @Column(name = "product_code", updatable = false, length = 40)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "mandate_type", nullable = false, length = 10)
    private MandateType mandateType;

    @Column(name = "min_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal minBalance;

    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyLimit;

    @Column(name = "overdraft_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal overdraftLimit;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
        // Required by JPA.
    }

    private Account(String organizationId, UUID customerId, AccountType accountType, Currency currency,
                    String accountNumber, String productCode, MandateType mandateType,
                    BigDecimal minBalance, BigDecimal dailyLimit, BigDecimal overdraftLimit) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.customerId = customerId;
        this.accountType = accountType;
        this.accountNumber = accountNumber;
        this.status = AccountStatus.ACTIVE;
        this.currencyCode = currency.getCurrencyCode();
        this.balanceAmount = Money.zero(currency).amount();
        this.productCode = productCode;
        this.mandateType = mandateType;
        this.minBalance = scale(minBalance, currency);
        this.dailyLimit = scale(dailyLimit, currency);
        this.overdraftLimit = scale(overdraftLimit, currency);
        this.createdAt = now;
        this.updatedAt = now;
    }

    private static BigDecimal scale(BigDecimal value, Currency currency) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.setScale(currency.getDefaultFractionDigits(), Money.ROUNDING);
    }

    /** Factory: open a simple account (single mandate, no product, zero limits). */
    public static Account open(String organizationId, UUID customerId, AccountType accountType,
                               Currency currency, String accountNumber) {
        return new Account(organizationId, customerId, accountType, currency, accountNumber,
                null, MandateType.SINGLE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Factory: open an account against a product, inheriting its currency, type and limits. */
    public static Account openFromProduct(String organizationId, UUID customerId, String accountNumber,
                                          Product product, MandateType mandateType) {
        return new Account(organizationId, customerId, product.getAccountType(), product.currency(),
                accountNumber, product.getCode(), mandateType, product.getMinBalance(),
                product.getDailyLimit(), product.getOverdraftLimit());
    }

    public Money balance() {
        return Money.of(balanceAmount, Currency.getInstance(currencyCode));
    }

    /** Funds available to spend: current balance plus any agreed overdraft. */
    public Money availableToSpend() {
        return Money.of(balanceAmount.add(overdraftLimit), Currency.getInstance(currencyCode));
    }

    /** Whether a withdrawal is permitted: it must not exceed the available (incl. overdraft) funds. */
    public boolean canWithdraw(Money amount) {
        return amount.amount().compareTo(availableToSpend().amount()) <= 0;
    }

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getProductCode() {
        return productCode;
    }

    public MandateType getMandateType() {
        return mandateType;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
