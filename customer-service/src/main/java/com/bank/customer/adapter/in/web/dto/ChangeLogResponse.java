package com.bank.customer.adapter.in.web.dto;

import com.bank.customer.domain.ChangeLog;
import com.bank.customer.domain.ChangeType;
import java.time.Instant;

/** Response view of a change-log entry (ADR-001). */
public record ChangeLogResponse(
        String organizationId,
        String entityType,
        String entityId,
        ChangeType changeType,
        String actor,
        String details,
        Instant occurredAt) {

    public static ChangeLogResponse from(ChangeLog c) {
        return new ChangeLogResponse(
                c.getOrganizationId(), c.getEntityType(), c.getEntityId(),
                c.getChangeType(), c.getActor(), c.getDetails(), c.getOccurredAt());
    }
}
