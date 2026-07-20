package com.bank.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of an entity add/update/delete (ADR-001), written in the same transaction as
 * the change and scoped by organization. Rows are never updated or deleted.
 */
@Entity
@Table(name = "change_log")
public class ChangeLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
    private String organizationId;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false, length = 60)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, updatable = false, length = 10)
    private ChangeType changeType;

    @Column(nullable = false, updatable = false, length = 80)
    private String actor;

    @Column(updatable = false, length = 2000)
    private String details;

    @Column(name = "correlation_id", updatable = false, length = 60)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ChangeLog() {
        // Required by JPA.
    }

    public ChangeLog(String organizationId, String entityType, String entityId, ChangeType changeType,
                     String actor, String details, String correlationId) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.changeType = changeType;
        this.actor = actor;
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

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public String getActor() {
        return actor;
    }

    public String getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
