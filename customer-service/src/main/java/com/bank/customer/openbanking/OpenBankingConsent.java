package com.bank.customer.openbanking;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An Open Banking consent: a customer authorizes a Third-Party Provider to access scoped resources
 * for a bounded period. Consent must be authorised with strong customer authentication (an SCA
 * reference) before it becomes ACTIVE, and can be revoked at any time (FR-CHN-002/003).
 */
@Entity
@Table(name = "open_banking_consent")
public class OpenBankingConsent {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(
      name = "consent_reference",
      nullable = false,
      unique = true,
      updatable = false,
      length = 60)
  private String consentReference;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(nullable = false, updatable = false, length = 120)
  private String tpp;

  /** Comma-separated scope names. */
  @Column(nullable = false, updatable = false, length = 200)
  private String scopes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ConsentStatus status;

  @Column(name = "sca_reference", length = 80)
  private String scaReference;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected OpenBankingConsent() {
    // Required by JPA.
  }

  private OpenBankingConsent(
      String consentReference,
      UUID customerId,
      String tpp,
      Set<ConsentScope> scopes,
      Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.consentReference = consentReference;
    this.customerId = customerId;
    this.tpp = tpp;
    this.scopes = scopes.stream().map(Enum::name).collect(Collectors.joining(","));
    this.status = ConsentStatus.AWAITING_AUTHORISATION;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  public static OpenBankingConsent request(
      String consentReference,
      UUID customerId,
      String tpp,
      Set<ConsentScope> scopes,
      Instant expiresAt) {
    if (scopes == null || scopes.isEmpty()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "At least one scope is required");
    }
    return new OpenBankingConsent(consentReference, customerId, tpp, scopes, expiresAt);
  }

  /** Authorise the consent with an SCA reference (proof of strong customer authentication). */
  public void authorise(String scaReference) {
    refreshExpiry();
    if (status != ConsentStatus.AWAITING_AUTHORISATION) {
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION, "Consent cannot be authorised from status " + status);
    }
    if (scaReference == null || scaReference.isBlank()) {
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION,
          "Strong customer authentication is required to authorise consent");
    }
    this.scaReference = scaReference;
    this.status = ConsentStatus.ACTIVE;
  }

  public void revoke() {
    this.status = ConsentStatus.REVOKED;
  }

  /** Lazily expire the consent if its validity window has passed. */
  public void refreshExpiry() {
    if ((status == ConsentStatus.ACTIVE || status == ConsentStatus.AWAITING_AUTHORISATION)
        && Instant.now().isAfter(expiresAt)) {
      this.status = ConsentStatus.EXPIRED;
    }
  }

  public boolean isActive() {
    refreshExpiry();
    return status == ConsentStatus.ACTIVE;
  }

  public Set<ConsentScope> scopeSet() {
    EnumSet<ConsentScope> set = EnumSet.noneOf(ConsentScope.class);
    for (String s : scopes.split(",")) {
      set.add(ConsentScope.valueOf(s));
    }
    return set;
  }

  public UUID getId() {
    return id;
  }

  public String getConsentReference() {
    return consentReference;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getTpp() {
    return tpp;
  }

  public String getScopes() {
    return scopes;
  }

  public ConsentStatus getStatus() {
    return status;
  }

  public String getScaReference() {
    return scaReference;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
