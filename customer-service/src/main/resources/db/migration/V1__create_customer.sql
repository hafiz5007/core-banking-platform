-- Sprint 1: customer (party) and consent tables.

CREATE TABLE customer (
    id                  UUID         NOT NULL,
    cif                 VARCHAR(12)  NOT NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    date_of_birth       DATE         NOT NULL,
    nationality         CHAR(2)      NOT NULL,
    email               VARCHAR(320) NOT NULL,
    phone               VARCHAR(30),
    tax_id              VARCHAR(50),
    status              VARCHAR(20)  NOT NULL,
    kyc_status          VARCHAR(20)  NOT NULL,
    kyc_evidence_ref    VARCHAR(100),
    screening_match     BOOLEAN      NOT NULL DEFAULT FALSE,
    screening_case_ref  VARCHAR(100),
    risk_rating         VARCHAR(10),
    kyc_refresh_due     DATE,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_customer PRIMARY KEY (id),
    CONSTRAINT uq_customer_cif UNIQUE (cif),
    CONSTRAINT chk_customer_status CHECK (status IN ('PENDING','ACTIVE','BLOCKED','CLOSED')),
    CONSTRAINT chk_customer_kyc CHECK (kyc_status IN ('NOT_STARTED','VERIFIED','REFERRED','FAILED'))
);

CREATE TABLE consent (
    id           UUID        NOT NULL,
    customer_id  UUID        NOT NULL,
    consent_type VARCHAR(30) NOT NULL,
    granted      BOOLEAN     NOT NULL,
    channel      VARCHAR(30) NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_consent PRIMARY KEY (id),
    CONSTRAINT fk_consent_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

CREATE INDEX idx_consent_customer_id ON consent (customer_id);
