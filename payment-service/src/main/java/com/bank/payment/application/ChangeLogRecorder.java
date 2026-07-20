package com.bank.payment.application;

import com.bank.common.tenant.TenantContext;
import com.bank.common.web.CorrelationIdFilter;
import com.bank.payment.adapter.out.persistence.ChangeLogRepository;
import com.bank.payment.domain.ChangeLog;
import com.bank.payment.domain.ChangeType;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public List<ChangeLog> history(String entityType, String entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId);
    }
}
