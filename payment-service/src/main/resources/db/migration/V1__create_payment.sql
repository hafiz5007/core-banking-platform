-- Sprint 4: payment and transactional-outbox tables.

CREATE TABLE payment (
    id                UUID         NOT NULL,
    idempotency_key   VARCHAR(80)  NOT NULL,
    payment_type      VARCHAR(20)  NOT NULL,
    debtor_account    VARCHAR(40)  NOT NULL,
    creditor_account  VARCHAR(40)  NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    currency_code     CHAR(3)      NOT NULL,
    narrative         VARCHAR(280) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    ledger_entry_id   UUID,
    reversal_entry_id UUID,
    failure_reason    VARCHAR(280),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_payment PRIMARY KEY (id),
    CONSTRAINT uq_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_payment_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_payment_status CHECK (status IN
        ('RECEIVED','VALIDATED','SCREENED','POSTED','CONFIRMED','REJECTED','COMPENSATED'))
);

CREATE INDEX idx_payment_status ON payment (status);

CREATE TABLE outbox_event (
    id             UUID        NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     VARCHAR(60) NOT NULL,
    payload        TEXT        NOT NULL,
    status         VARCHAR(12) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    published_at   TIMESTAMPTZ,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PUBLISHED'))
);

CREATE INDEX idx_outbox_status_created ON outbox_event (status, created_at);
