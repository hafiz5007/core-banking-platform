package com.bank.account.application;

import com.bank.account.adapter.out.persistence.ChangeLogRepository;
import com.bank.account.domain.ChangeLog;
import com.bank.account.domain.ChangeType;
import com.bank.common.tenant.TenantContext;
import com.bank.common.web.CorrelationIdFilter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records entity add/update/delete events into the append-only change log (ADR-001). Called from
 * the application layer in the same transaction as the change, so the audit row commits atomically
 * with it. Replicate this pattern in the other services.
 */
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

    @Transactional(readOnly = true)
    public List<ChangeLog> history(String entityType, String entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId);
    }
}
