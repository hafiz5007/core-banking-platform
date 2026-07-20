-- Sprint 10: RBAC roles, maker-checker approvals, and the immutable audit trail.

CREATE TABLE role (
    id          UUID         NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    permissions VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_role PRIMARY KEY (id),
    CONSTRAINT uq_role_name UNIQUE (name)
);

CREATE TABLE approval_request (
    id              UUID         NOT NULL,
    action          VARCHAR(80)  NOT NULL,
    payload         VARCHAR(2000) NOT NULL,
    maker           VARCHAR(80)  NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    checker         VARCHAR(80),
    decision_reason VARCHAR(280),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    decided_at      TIMESTAMPTZ,
    CONSTRAINT pk_approval_request PRIMARY KEY (id),
    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);

CREATE TABLE audit_event (
    id             UUID         NOT NULL,
    actor          VARCHAR(80)  NOT NULL,
    action         VARCHAR(80)  NOT NULL,
    target         VARCHAR(120) NOT NULL,
    details        VARCHAR(2000),
    correlation_id VARCHAR(60),
    occurred_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);

CREATE INDEX idx_audit_event_actor ON audit_event (actor, occurred_at);
