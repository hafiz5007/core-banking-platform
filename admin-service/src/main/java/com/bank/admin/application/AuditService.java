package com.bank.admin.application;

import com.bank.admin.adapter.out.persistence.AuditEventRepository;
import com.bank.admin.domain.AuditEvent;
import com.bank.common.web.CorrelationIdFilter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records and queries the append-only audit trail (FR-BO-003). */
@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditEvent record(String actor, String action, String target, String details) {
        return repository.save(new AuditEvent(actor, action, target, details, CorrelationIdFilter.current()));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> byActor(String actor) {
        return repository.findTop200ByActorOrderByOccurredAtDesc(actor);
    }
}
