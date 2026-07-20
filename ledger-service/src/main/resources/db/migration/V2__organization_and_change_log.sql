-- ADR-002 (tenancy) + ADR-001 (change history) rollout for ledger-service.

ALTER TABLE ledger_account ADD COLUMN organization_id VARCHAR(60) NOT NULL DEFAULT 'DEFAULT';
CREATE INDEX idx_ledger_account_organization ON ledger_account (organization_id);

CREATE TABLE change_log (
    id              UUID         NOT NULL,
    organization_id VARCHAR(60)  NOT NULL,
    entity_type     VARCHAR(60)  NOT NULL,
    entity_id       VARCHAR(60)  NOT NULL,
    change_type     VARCHAR(10)  NOT NULL,
    actor           VARCHAR(80)  NOT NULL,
    details         VARCHAR(2000),
    correlation_id  VARCHAR(60),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_change_log PRIMARY KEY (id),
    CONSTRAINT chk_change_type CHECK (change_type IN ('CREATE','UPDATE','DELETE'))
);

CREATE INDEX idx_change_log_entity ON change_log (entity_type, entity_id, occurred_at);
CREATE INDEX idx_change_log_org ON change_log (organization_id, occurred_at);
