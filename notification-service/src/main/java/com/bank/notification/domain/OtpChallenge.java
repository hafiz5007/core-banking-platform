package com.bank.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A one-time passcode challenge. Only a hash of the code is stored; codes expire and are
 * single-use. Verification is rate-limited by an attempt counter.
 */
@Entity
@Table(name = "otp_challenge")
public class OtpChallenge {

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 320)
    private String recipient;

    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private boolean consumed;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OtpChallenge() {
        // Required by JPA.
    }

    public OtpChallenge(String recipient, String codeHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.recipient = recipient;
        this.codeHash = codeHash;
        this.consumed = false;
        this.attempts = 0;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public boolean verify(String candidateHash) {
        attempts++;
        if (consumed || Instant.now().isAfter(expiresAt) || attempts > MAX_ATTEMPTS) {
            return false;
        }
        boolean ok = codeHash.equals(candidateHash);
        if (ok) {
            consumed = true;
        }
        return ok;
    }

    public UUID getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
