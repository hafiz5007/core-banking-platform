package com.bank.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A Kafka record this service has already handled (ADR-014). Kafka delivers at least once, so the
 * consumer records the record's coordinate and refuses to act on it twice.
 */
@Entity
@Table(name = "processed_notifications")
public class ProcessedNotification {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, updatable = false, length = 120)
  private String topic;

  @Column(name = "partition_id", nullable = false, updatable = false)
  private int partitionId;

  @Column(name = "record_offset", nullable = false, updatable = false)
  private long recordOffset;

  @Column(name = "event_type", nullable = false, updatable = false, length = 60)
  private String eventType;

  @Column(name = "processed_at", nullable = false, updatable = false)
  private Instant processedAt;

  protected ProcessedNotification() {
    // Required by JPA.
  }

  public ProcessedNotification(String topic, int partitionId, long recordOffset, String eventType) {
    this.id = UUID.randomUUID();
    this.topic = topic;
    this.partitionId = partitionId;
    this.recordOffset = recordOffset;
    this.eventType = eventType;
    this.processedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getTopic() {
    return topic;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public long getRecordOffset() {
    return recordOffset;
  }

  public String getEventType() {
    return eventType;
  }
}
