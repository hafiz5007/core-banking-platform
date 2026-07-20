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
import java.util.Currency;
import java.util.UUID;

/**
 * A direct-debit mandate authorizing a payee to pull payments from a payer's account up to a
 * per-collection ceiling, until cancelled (FR-PAY-005).
 */
@Entity
@Table(name = "direct_debit_mandate")
public class DirectDebitMandate {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "mandate_reference", nullable = false, unique = true, updatable = false, length = 60)
    private String mandateReference;

    @Column(name = "payer_account", nullable = false, updatable = false, length = 40)
    private String payerAccount;

    @Column(name = "payee_account", nullable = false, updatable = false, length = 40)
    private String payeeAccount;

    @Column(name = "max_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MandateStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DirectDebitMandate() {
        // Required by JPA.
    }

    private DirectDebitMandate(String mandateReference, String payerAccount, String payeeAccount,
                               Money maxAmount) {
        this.id = UUID.randomUUID();
        this.mandateReference = mandateReference;
        this.payerAccount = payerAccount;
        this.payeeAccount = payeeAccount;
        this.maxAmount = maxAmount.amount();
        this.currencyCode = maxAmount.currency().getCurrencyCode();
        this.status = MandateStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public static DirectDebitMandate create(String mandateReference, String payerAccount,
                                            String payeeAccount, Money maxAmount) {
        return new DirectDebitMandate(mandateReference, payerAccount, payeeAccount, maxAmount);
    }

    public boolean canCollect(Money amount) {
        return status == MandateStatus.ACTIVE
                && amount.currency().getCurrencyCode().equals(currencyCode)
                && amount.amount().compareTo(maxAmount) <= 0
                && amount.amount().signum() > 0;
    }

    public void cancel() {
        this.status = MandateStatus.CANCELLED;
    }

    public Money maxMoney() {
        return Money.of(maxAmount, Currency.getInstance(currencyCode));
    }

    public UUID getId() {
        return id;
    }

    public String getMandateReference() {
        return mandateReference;
    }

    public String getPayerAccount() {
        return payerAccount;
    }

    public String getPayeeAccount() {
        return payeeAccount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public MandateStatus getStatus() {
        return status;
    }
}
