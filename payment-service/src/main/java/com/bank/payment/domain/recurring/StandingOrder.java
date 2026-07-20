package com.bank.payment.domain.recurring;

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
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

/**
 * A standing order: a recurring intrabank credit transfer the bank executes automatically on each
 * due date until cancelled or past its end date (FR-PAY-005).
 */
@Entity
@Table(name = "standing_order")
public class StandingOrder {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "debtor_account", nullable = false, updatable = false, length = 40)
    private String debtorAccount;

    @Column(name = "creditor_account", nullable = false, updatable = false, length = 40)
    private String creditorAccount;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, updatable = false, length = 280)
    private String narrative;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Frequency frequency;

    @Column(name = "next_run_date", nullable = false)
    private LocalDate nextRunDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StandingOrderStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StandingOrder() {
        // Required by JPA.
    }

    private StandingOrder(String debtorAccount, String creditorAccount, Money amount, String narrative,
                          Frequency frequency, LocalDate startDate, LocalDate endDate) {
        this.id = UUID.randomUUID();
        this.debtorAccount = debtorAccount;
        this.creditorAccount = creditorAccount;
        this.amount = amount.amount();
        this.currencyCode = amount.currency().getCurrencyCode();
        this.narrative = narrative;
        this.frequency = frequency;
        this.nextRunDate = startDate;
        this.endDate = endDate;
        this.status = StandingOrderStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public static StandingOrder create(String debtorAccount, String creditorAccount, Money amount,
                                       String narrative, Frequency frequency,
                                       LocalDate startDate, LocalDate endDate) {
        return new StandingOrder(debtorAccount, creditorAccount, amount, narrative,
                frequency, startDate, endDate);
    }

    public boolean isDue(LocalDate on) {
        return status == StandingOrderStatus.ACTIVE && !nextRunDate.isAfter(on);
    }

    /** Advance to the next run date; complete the order once it passes its end date. */
    public void advance() {
        LocalDate next = frequency.next(nextRunDate);
        if (endDate != null && next.isAfter(endDate)) {
            this.status = StandingOrderStatus.COMPLETED;
        }
        this.nextRunDate = next;
    }

    public void cancel() {
        this.status = StandingOrderStatus.CANCELLED;
    }

    public Money money() {
        return Money.of(amount, Currency.getInstance(currencyCode));
    }

    public UUID getId() {
        return id;
    }

    public String getDebtorAccount() {
        return debtorAccount;
    }

    public String getCreditorAccount() {
        return creditorAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getNarrative() {
        return narrative;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public LocalDate getNextRunDate() {
        return nextRunDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public StandingOrderStatus getStatus() {
        return status;
    }
}
