package com.bank.notification.application;

import com.bank.common.tenant.TenantContext;
import com.bank.common.web.CorrelationIdFilter;
import com.bank.notification.adapter.out.persistence.ChangeLogRepository;
import com.bank.notification.domain.ChangeLog;
import com.bank.notification.domain.ChangeType;
import org.springframework.stereotype.Service;

/** Records entity add/update/delete events into the append-only change log (ADR-001). */
@Service
public class ChangeLogRecorder {

  private final ChangeLogRepository repository;

  public ChangeLogRecorder(ChangeLogRepository repository) {
    this.repository = repository;
  }

  public void record(
      String entityType, String entityId, ChangeType changeType, String actor, String details) {
    repository.save(
        new ChangeLog(
            TenantContext.getOrDefault(),
            entityType,
            entityId,
            changeType,
            actor == null ? "system" : actor,
            details,
            CorrelationIdFilter.current()));
  }
}
