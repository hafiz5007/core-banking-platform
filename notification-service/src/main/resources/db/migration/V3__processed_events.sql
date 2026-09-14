-- ADR-014: Kafka delivery is at-least-once, so the consumer records what it has already handled.
-- The natural key is the Kafka coordinate (topic, partition, offset), which is unique per record.

CREATE TABLE processed_event (
    id            UUID         NOT NULL,
    topic         VARCHAR(120) NOT NULL,
    partition_id  INTEGER      NOT NULL,
    record_offset BIGINT       NOT NULL,
    event_type    VARCHAR(60)  NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (id),
    CONSTRAINT uq_processed_event_coordinate UNIQUE (topic, partition_id, record_offset)
);

CREATE INDEX idx_processed_event_processed_at ON processed_event (processed_at);
