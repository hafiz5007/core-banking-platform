-- Sprint 9: Open Banking (TPP) consents.

CREATE TABLE open_banking_consent (
    id                UUID         NOT NULL,
    consent_reference VARCHAR(60)  NOT NULL,
    customer_id       UUID         NOT NULL,
    tpp               VARCHAR(120) NOT NULL,
    scopes            VARCHAR(200) NOT NULL,
    status            VARCHAR(24)  NOT NULL,
    sca_reference     VARCHAR(80),
    expires_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_open_banking_consent PRIMARY KEY (id),
    CONSTRAINT uq_ob_consent_reference UNIQUE (consent_reference),
    CONSTRAINT chk_ob_consent_status CHECK (status IN
        ('AWAITING_AUTHORISATION','ACTIVE','REVOKED','EXPIRED'))
);

CREATE INDEX idx_ob_consent_customer ON open_banking_consent (customer_id);
