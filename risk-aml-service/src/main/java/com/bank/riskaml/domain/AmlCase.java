package com.bank.riskaml.domain;

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

/** An AML investigation case, optionally resulting in a Suspicious Activity Report (SAR). */
@Entity
@Table(name = "aml_case")
public class AmlCase {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
  private String organizationId;

  @Column(name = "account_ref", nullable = false, updatable = false, length = 40)
  private String accountRef;

  @Column(name = "rule_code", nullable = false, updatable = false, length = 40)
  private String ruleCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private CaseStatus status;

  @Column(name = "sar_filed", nullable = false)
  private boolean sarFiled;

  @Column(length = 500)
  private String resolution;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  protected AmlCase() {
    // Required by JPA.
  }

  public AmlCase(String accountRef, String ruleCode) {
    this.id = UUID.randomUUID();
    this.organizationId = com.bank.common.tenant.TenantContext.getOrDefault();
    this.accountRef = accountRef;
    this.ruleCode = ruleCode;
    this.status = CaseStatus.OPEN;
    this.sarFiled = false;
    this.createdAt = Instant.now();
  }

  public void close(String resolution, boolean fileSar) {
    if (status == CaseStatus.CLOSED) {
      throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Case already closed");
    }
    this.resolution = resolution;
    this.sarFiled = fileSar;
    this.status = CaseStatus.CLOSED;
    this.closedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public String getAccountRef() {
    return accountRef;
  }

  public String getRuleCode() {
    return ruleCode;
  }

  public CaseStatus getStatus() {
    return status;
  }

  public boolean isSarFiled() {
    return sarFiled;
  }

  public String getResolution() {
    return resolution;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }
}
