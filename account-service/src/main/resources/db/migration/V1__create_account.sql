-- Sprint 0 walking skeleton: account table.
-- Money is stored as an exact NUMERIC amount + ISO-4217 currency code, never floating point.

CREATE TABLE account (
    id             UUID         NOT NULL,
    account_number VARCHAR(34)  NOT NULL,
    customer_id    UUID         NOT NULL,
    account_type   VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    balance_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency_code  CHAR(3)      NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_account PRIMARY KEY (id),
    CONSTRAINT uq_account_number UNIQUE (account_number),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE','DORMANT','BLOCKED','FROZEN','CLOSED')),
    CONSTRAINT chk_account_type CHECK (account_type IN ('CURRENT','SAVINGS','TERM_DEPOSIT'))
);

CREATE INDEX idx_account_customer_id ON account (customer_id);
