package com.bank.admin.adapter.out.persistence;

import com.bank.admin.domain.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
  List<AuditEvent> findTop200ByActorOrderByOccurredAtDesc(String actor);
}
