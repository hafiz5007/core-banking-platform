package com.bank.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One figure in the reporting projection (FR-RPT-001/002/003). Services publish metrics (e.g.
 * posting counts, settlement totals) keyed by business date; reports aggregate these without
 * touching the transactional path.
 */
@Entity
@Table(name = "report_metric")
public class ReportMetric {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
  private String organizationId;

  @Column(name = "business_date", nullable = false, updatable = false)
  private LocalDate businessDate;

  @Column(name = "metric_key", nullable = false, updatable = false, length = 80)
  private String metricKey;

  @Column(name = "metric_value", nullable = false, updatable = false, precision = 23, scale = 4)
  private BigDecimal metricValue;

  @Column(nullable = false, updatable = false, length = 60)
  private String source;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  protected ReportMetric() {
    // Required by JPA.
  }

  public ReportMetric(
      LocalDate businessDate, String metricKey, BigDecimal metricValue, String source) {
    this.id = UUID.randomUUID();
    this.organizationId = com.bank.common.tenant.TenantContext.getOrDefault();
    this.businessDate = businessDate;
    this.metricKey = metricKey;
    this.metricValue = metricValue;
    this.source = source;
    this.recordedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public LocalDate getBusinessDate() {
    return businessDate;
  }

  public String getMetricKey() {
    return metricKey;
  }

  public BigDecimal getMetricValue() {
    return metricValue;
  }

  public String getSource() {
    return source;
  }
}
