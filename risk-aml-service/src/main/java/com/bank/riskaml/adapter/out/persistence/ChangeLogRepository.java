package com.bank.riskaml.adapter.out.persistence;

import com.bank.riskaml.domain.ChangeLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeLogRepository extends JpaRepository<ChangeLog, UUID> {
    List<ChangeLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId);
}
