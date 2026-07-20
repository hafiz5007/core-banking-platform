-- Sprint 10: AML cases and alerts.

CREATE TABLE aml_case (
    id           UUID         NOT NULL,
    account_ref  VARCHAR(40)  NOT NULL,
    rule_code    VARCHAR(40)  NOT NULL,
    status       VARCHAR(12)  NOT NULL,
    sar_filed    BOOLEAN      NOT NULL DEFAULT FALSE,
    resolution   VARCHAR(500),
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    closed_at    TIMESTAMPTZ,
    CONSTRAINT pk_aml_case PRIMARY KEY (id),
    CONSTRAINT chk_aml_case_status CHECK (status IN ('OPEN','IN_REVIEW','CLOSED'))
);

CREATE TABLE aml_alert (
    id              UUID         NOT NULL,
    transaction_ref VARCHAR(80)  NOT NULL,
    account_ref     VARCHAR(40)  NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    currency_code   CHAR(3)      NOT NULL,
    rule_code       VARCHAR(40)  NOT NULL,
    case_id         UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_aml_alert PRIMARY KEY (id),
    CONSTRAINT fk_aml_alert_case FOREIGN KEY (case_id) REFERENCES aml_case (id)
);

CREATE INDEX idx_aml_alert_case ON aml_alert (case_id);
CREATE INDEX idx_aml_alert_account ON aml_alert (account_ref);
