package com.bank.admin.domain;

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
import java.util.UUID;

/**
 * A maker-checker (four-eyes) approval request (FR-BO-002): one user (the maker) submits a
 * sensitive action; a different user (the checker) must approve or reject it. The same person can
 * never do both.
 */
@Entity
@Table(name = "approval_request")
public class ApprovalRequest {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
  private String organizationId;

  @Column(nullable = false, updatable = false, length = 80)
  private String action;

  @Column(nullable = false, updatable = false, length = 2000)
  private String payload;

  @Column(nullable = false, updatable = false, length = 80)
  private String maker;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ApprovalStatus status;

  @Column(length = 80)
  private String checker;

  @Column(name = "decision_reason", length = 280)
  private String decisionReason;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  protected ApprovalRequest() {
    // Required by JPA.
  }

  public ApprovalRequest(String action, String payload, String maker) {
    this.id = UUID.randomUUID();
    this.organizationId = com.bank.common.tenant.TenantContext.getOrDefault();
    this.action = action;
    this.payload = payload;
    this.maker = maker;
    this.status = ApprovalStatus.PENDING;
    this.createdAt = Instant.now();
  }

  public void approve(String checker) {
    decide(checker, ApprovalStatus.APPROVED, null);
  }

  public void reject(String checker, String reason) {
    decide(checker, ApprovalStatus.REJECTED, reason);
  }

  private void decide(String checker, ApprovalStatus outcome, String reason) {
    if (status != ApprovalStatus.PENDING) {
      throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Request already decided");
    }
    if (checker == null || checker.isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Checker is required");
    }
    if (checker.equals(maker)) {
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION,
          "Four-eyes violation: the checker must differ from the maker");
    }
    this.checker = checker;
    this.status = outcome;
    this.decisionReason = reason;
    this.decidedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getAction() {
    return action;
  }

  public String getPayload() {
    return payload;
  }

  public String getMaker() {
    return maker;
  }

  public ApprovalStatus getStatus() {
    return status;
  }

  public String getChecker() {
    return checker;
  }

  public String getDecisionReason() {
    return decisionReason;
  }
}
