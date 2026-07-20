package com.bank.payment.domain;

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
 * Canonical payment aggregate. The same object represents every rail; only the {@link PaymentType}
 * and the adapter that fulfils it differ. State transitions are made through the methods below so
 * an illegal lifecycle jump cannot occur.
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
    private String organizationId;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 80)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, updatable = false, length = 20)
    private PaymentType type;

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

    @Column(name = "target_currency_code", updatable = false, length = 3)
    private String targetCurrencyCode;

    @Column(name = "target_amount", precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "fx_rate", precision = 18, scale = 8)
    private BigDecimal fxRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "reversal_entry_id")
    private UUID reversalEntryId;

    @Column(name = "scheme_reference", length = 60)
    private String schemeReference;

    @Column(name = "failure_reason", length = 280)
    private String failureReason;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // Required by JPA.
    }

    private Payment(String idempotencyKey, PaymentType type, String debtorAccount,
                    String creditorAccount, Money amount, String narrative, String targetCurrencyCode) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.organizationId = com.bank.common.tenant.TenantContext.getOrDefault();
        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.debtorAccount = debtorAccount;
        this.creditorAccount = creditorAccount;
        this.amount = amount.amount();
        this.currencyCode = amount.currency().getCurrencyCode();
        this.narrative = narrative;
        this.targetCurrencyCode = targetCurrencyCode;
        this.status = PaymentStatus.RECEIVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Payment received(String idempotencyKey, PaymentType type, String debtorAccount,
                                   String creditorAccount, Money amount, String narrative) {
        return new Payment(idempotencyKey, type, debtorAccount, creditorAccount, amount, narrative, null);
    }

    public static Payment received(String idempotencyKey, PaymentType type, String debtorAccount,
                                   String creditorAccount, Money amount, String narrative,
                                   String targetCurrencyCode) {
        return new Payment(idempotencyKey, type, debtorAccount, creditorAccount, amount, narrative,
                targetCurrencyCode);
    }

    /** Record the FX conversion applied to a cross-border payment. */
    public void applyFx(java.math.BigDecimal fxRate, Money targetMoney) {
        this.fxRate = fxRate;
        this.targetAmount = targetMoney.amount();
        this.targetCurrencyCode = targetMoney.currency().getCurrencyCode();
        this.updatedAt = Instant.now();
    }

    public void markValidated() {
        transitionTo(PaymentStatus.VALIDATED);
    }

    public void markScreened() {
        transitionTo(PaymentStatus.SCREENED);
    }

    public void markPosted(UUID ledgerEntryId) {
        this.ledgerEntryId = ledgerEntryId;
        transitionTo(PaymentStatus.POSTED);
    }

    public void markSubmitted(String schemeReference) {
        this.schemeReference = schemeReference;
        transitionTo(PaymentStatus.SUBMITTED);
    }

    public void markSettled() {
        transitionTo(PaymentStatus.SETTLED);
    }

    public void markConfirmed() {
        transitionTo(PaymentStatus.CONFIRMED);
    }

    public void markReturned(UUID reversalEntryId, String reason) {
        this.reversalEntryId = reversalEntryId;
        this.failureReason = reason;
        transitionTo(PaymentStatus.RETURNED);
    }

    public void markRejected(String reason) {
        this.failureReason = reason;
        transitionTo(PaymentStatus.REJECTED);
    }

    public void markCompensated(UUID reversalEntryId, String reason) {
        this.reversalEntryId = reversalEntryId;
        this.failureReason = reason;
        transitionTo(PaymentStatus.COMPENSATED);
    }

    private void transitionTo(PaymentStatus next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public Money money() {
        return Money.of(amount, Currency.getInstance(currencyCode));
    }

    public UUID getId() {
        return id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PaymentType getType() {
        return type;
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

    public PaymentStatus getStatus() {
        return status;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }

    public UUID getReversalEntryId() {
        return reversalEntryId;
    }

    public String getSchemeReference() {
        return schemeReference;
    }

    public String getTargetCurrencyCode() {
        return targetCurrencyCode;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
