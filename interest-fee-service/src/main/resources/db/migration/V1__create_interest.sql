-- Sprint 7: interest positions and fee charges.

CREATE TABLE interest_position (
    id                  UUID         NOT NULL,
    account_code        VARCHAR(40)  NOT NULL,
    currency_code       CHAR(3)      NOT NULL,
    annual_rate_percent NUMERIC(9, 4)  NOT NULL,
    principal           NUMERIC(19, 4) NOT NULL,
    accrued_interest    NUMERIC(23, 8) NOT NULL DEFAULT 0,
    day_count           VARCHAR(10)  NOT NULL,
    last_accrual_date   DATE         NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_interest_position PRIMARY KEY (id),
    CONSTRAINT uq_interest_position_account UNIQUE (account_code),
    CONSTRAINT chk_interest_day_count CHECK (day_count IN ('ACT_365','ACT_360'))
);

CREATE TABLE fee_charge (
    id              UUID         NOT NULL,
    account_code    VARCHAR(40)  NOT NULL,
    fee_type        VARCHAR(20)  NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    currency_code   CHAR(3)      NOT NULL,
    ledger_entry_id UUID,
    applied_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_fee_charge PRIMARY KEY (id),
    CONSTRAINT chk_fee_type CHECK (fee_type IN ('MAINTENANCE','TRANSACTION','PENALTY','FX_MARKUP'))
);

CREATE INDEX idx_fee_charge_account ON fee_charge (account_code);
