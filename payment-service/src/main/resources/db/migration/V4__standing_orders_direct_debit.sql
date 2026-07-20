-- Sprint 7 (cont.): standing orders and direct-debit mandates.

CREATE TABLE standing_order (
    id               UUID         NOT NULL,
    debtor_account   VARCHAR(40)  NOT NULL,
    creditor_account VARCHAR(40)  NOT NULL,
    amount           NUMERIC(19, 4) NOT NULL,
    currency_code    CHAR(3)      NOT NULL,
    narrative        VARCHAR(280) NOT NULL,
    frequency        VARCHAR(10)  NOT NULL,
    next_run_date    DATE         NOT NULL,
    end_date         DATE,
    status           VARCHAR(10)  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_standing_order PRIMARY KEY (id),
    CONSTRAINT chk_so_frequency CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY')),
    CONSTRAINT chk_so_status CHECK (status IN ('ACTIVE','CANCELLED','COMPLETED'))
);

CREATE INDEX idx_standing_order_due ON standing_order (status, next_run_date);

CREATE TABLE direct_debit_mandate (
    id                UUID         NOT NULL,
    mandate_reference VARCHAR(60)  NOT NULL,
    payer_account     VARCHAR(40)  NOT NULL,
    payee_account     VARCHAR(40)  NOT NULL,
    max_amount        NUMERIC(19, 4) NOT NULL,
    currency_code     CHAR(3)      NOT NULL,
    status            VARCHAR(10)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_direct_debit_mandate PRIMARY KEY (id),
    CONSTRAINT uq_direct_debit_mandate_ref UNIQUE (mandate_reference),
    CONSTRAINT chk_dd_status CHECK (status IN ('ACTIVE','CANCELLED'))
);
