package com.bank.notification.adapter.out.persistence;

import com.bank.notification.domain.ProcessedNotification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedNotificationRepository
    extends JpaRepository<ProcessedNotification, UUID> {

  boolean existsByTopicAndPartitionIdAndRecordOffset(
      String topic, int partitionId, long recordOffset);
}
