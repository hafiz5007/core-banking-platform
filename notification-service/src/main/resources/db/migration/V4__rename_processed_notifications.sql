-- Align the consumer's idempotency table with the name ADR-014 specifies.
-- A rename in a new migration rather than an edit to V3: editing an applied migration changes its
-- Flyway checksum and breaks startup on any database that already ran it.

ALTER TABLE processed_event RENAME TO processed_notifications;

-- Postgres keeps the old constraint and index names through a table rename; carry them across so
-- the schema does not still read as `processed_event` in psql.
ALTER TABLE processed_notifications RENAME CONSTRAINT pk_processed_event TO pk_processed_notifications;
ALTER TABLE processed_notifications
    RENAME CONSTRAINT uq_processed_event_coordinate TO uq_processed_notifications_coordinate;
ALTER INDEX idx_processed_event_processed_at RENAME TO idx_processed_notifications_processed_at;
