package com.bank.payment.domain.directory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A registered biller that customers can pay (FR-PAY-010). */
@Entity
@Table(name = "biller")
public class Biller {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "biller_code", nullable = false, unique = true, updatable = false, length = 40)
    private String billerCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "settlement_account", nullable = false, length = 40)
    private String settlementAccount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Biller() {
        // Required by JPA.
    }

    public Biller(String billerCode, String name, String settlementAccount) {
        this.id = UUID.randomUUID();
        this.billerCode = billerCode;
        this.name = name;
        this.settlementAccount = settlementAccount;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getBillerCode() {
        return billerCode;
    }

    public String getName() {
        return name;
    }

    public String getSettlementAccount() {
        return settlementAccount;
    }
}
