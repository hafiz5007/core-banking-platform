package com.bank.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable, append-only audit record of a privileged action (FR-BO-003). Rows are never updated
 * or deleted; the table is the tamper-evident trail of who did what, when.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
    private String organizationId;

    @Column(nullable = false, updatable = false, length = 80)
    private String actor;

    @Column(nullable = false, updatable = false, length = 80)
    private String action;

    @Column(nullable = false, updatable = false, length = 120)
    private String target;

    @Column(updatable = false, length = 2000)
    private String details;

    @Column(name = "correlation_id", updatable = false, length = 60)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEvent() {
        // Required by JPA.
    }

    public AuditEvent(String actor, String action, String target, String details, String correlationId) {
        this.id = UUID.randomUUID();
        this.organizationId = com.bank.common.tenant.TenantContext.getOrDefault();
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.details = details;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getDetails() {
        return details;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
