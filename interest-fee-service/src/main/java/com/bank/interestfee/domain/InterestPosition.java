package com.bank.interestfee.domain;

import com.bank.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.UUID;

/**
 * Interest-bearing position for one account. Interest accrues daily on the principal at the annual
 * rate, accumulating in {@code accruedInterest} at high precision; capitalization rounds the accrued
 * amount to the currency, adds it to the principal, and resets the accrual.
 */
@Entity
@Table(name = "interest_position")
public class InterestPosition {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
    private String organizationId;

    @Column(name = "account_code", nullable = false, unique = true, length = 40)
    private String accountCode;

    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    @Column(name = "annual_rate_percent", nullable = false, precision = 9, scale = 4)
    private BigDecimal annualRatePercent;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal principal;

    @Column(name = "accrued_interest", nullable = false, precision = 23, scale = 8)
    private BigDecimal accruedInterest;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_count", nullable = false, length = 10)
    private DayCountBasis dayCount;

    @Column(name = "last_accrual_date", nullable = false)
    private LocalDate lastAccrualDate;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InterestPosition() {
        // Required by JPA.
    }

    private InterestPosition(String accountCode, Currency currency, BigDecimal annualRatePercent,
                             Money principal, DayCountBasis dayCount, LocalDate openedOn) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.organizationId = com.bank.common.tenant.TenantContext.getOrDefault();
        this.accountCode = accountCode;
        this.currencyCode = currency.getCurrencyCode();
        this.annualRatePercent = annualRatePercent;
        this.principal = principal.amount();
        this.accruedInterest = BigDecimal.ZERO.setScale(8);
        this.dayCount = dayCount;
        this.lastAccrualDate = openedOn;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static InterestPosition open(String accountCode, Currency currency, BigDecimal annualRatePercent,
                                        Money principal, DayCountBasis dayCount, LocalDate openedOn) {
        return new InterestPosition(accountCode, currency, annualRatePercent, principal, dayCount, openedOn);
    }

    /** Accrue interest from {@code lastAccrualDate} up to {@code upTo}. No-op if not in the future. */
    public void accrueTo(LocalDate upTo) {
        long days = ChronoUnit.DAYS.between(lastAccrualDate, upTo);
        if (days <= 0) {
            return;
        }
        BigDecimal dailyRate = annualRatePercent
                .divide(new BigDecimal("100"), 12, RoundingMode.HALF_EVEN)
                .divide(dayCount.denominator(), 12, RoundingMode.HALF_EVEN);
        BigDecimal interest = principal.multiply(dailyRate).multiply(BigDecimal.valueOf(days));
        this.accruedInterest = accruedInterest.add(interest);
        this.lastAccrualDate = upTo;
        this.updatedAt = Instant.now();
    }

    /**
     * Capitalize the accrued interest: round to the currency, add it to the principal, reset the
     * accrual, and return the amount to be posted to the ledger.
     */
    public Money capitalize() {
        Currency currency = Currency.getInstance(currencyCode);
        Money amount = Money.of(accruedInterest, currency);
        this.principal = principal.add(amount.amount());
        this.accruedInterest = BigDecimal.ZERO.setScale(8);
        this.updatedAt = Instant.now();
        return amount;
    }

    public Money accruedMoney() {
        return Money.of(accruedInterest, Currency.getInstance(currencyCode));
    }

    public UUID getId() {
        return id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getAnnualRatePercent() {
        return annualRatePercent;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getAccruedInterest() {
        return accruedInterest;
    }

    public DayCountBasis getDayCount() {
        return dayCount;
    }

    public LocalDate getLastAccrualDate() {
        return lastAccrualDate;
    }
}
