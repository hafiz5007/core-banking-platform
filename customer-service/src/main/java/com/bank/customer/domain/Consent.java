package com.bank.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A versioned, timestamped record of a consent the customer granted or withdrew. Consents are
 * append-only: a change of mind is a new row, never an in-place edit, preserving the audit history.
 */
@Entity
@Table(name = "consent")
public class Consent {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "consent_type", nullable = false, updatable = false, length = 30)
  private ConsentType consentType;

  @Column(nullable = false, updatable = false)
  private boolean granted;

  @Column(nullable = false, updatable = false, length = 30)
  private String channel;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  protected Consent() {
    // Required by JPA.
  }

  public Consent(UUID customerId, ConsentType consentType, boolean granted, String channel) {
    this.id = UUID.randomUUID();
    this.customerId = customerId;
    this.consentType = consentType;
    this.granted = granted;
    this.channel = channel;
    this.recordedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public ConsentType getConsentType() {
    return consentType;
  }

  public boolean isGranted() {
    return granted;
  }

  public String getChannel() {
    return channel;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }
}
