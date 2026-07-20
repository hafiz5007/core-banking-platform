package com.bank.card.adapter.out.persistence;

import com.bank.card.domain.ChangeLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeLogRepository extends JpaRepository<ChangeLog, UUID> {
    List<ChangeLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId);
}
