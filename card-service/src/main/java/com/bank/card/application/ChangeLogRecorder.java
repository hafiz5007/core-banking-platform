package com.bank.card.application;

import com.bank.card.adapter.out.persistence.ChangeLogRepository;
import com.bank.card.domain.ChangeLog;
import com.bank.card.domain.ChangeType;
import com.bank.common.tenant.TenantContext;
import com.bank.common.web.CorrelationIdFilter;
import org.springframework.stereotype.Service;

/** Records entity add/update/delete events into the append-only change log (ADR-001). */
@Service
public class ChangeLogRecorder {

    private final ChangeLogRepository repository;

    public ChangeLogRecorder(ChangeLogRepository repository) {
        this.repository = repository;
    }

    public void record(String entityType, String entityId, ChangeType changeType,
                       String actor, String details) {
        repository.save(new ChangeLog(
                TenantContext.getOrDefault(), entityType, entityId, changeType,
                actor == null ? "system" : actor, details, CorrelationIdFilter.current()));
    }
}
