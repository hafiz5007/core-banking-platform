-- Sprint 2: product catalogue, account mandates/limits, and joint holders.

CREATE TABLE product (
    id                    UUID         NOT NULL,
    code                  VARCHAR(40)  NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    account_type          VARCHAR(20)  NOT NULL,
    currency_code         VARCHAR(3)      NOT NULL,
    interest_rate_percent NUMERIC(9, 4)  NOT NULL,
    monthly_fee           NUMERIC(19, 4) NOT NULL,
    min_balance           NUMERIC(19, 4) NOT NULL,
    daily_limit           NUMERIC(19, 4) NOT NULL,
    overdraft_limit       NUMERIC(19, 4) NOT NULL,
    status                VARCHAR(10)  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT uq_product_code UNIQUE (code),
    CONSTRAINT chk_product_type CHECK (account_type IN ('CURRENT','SAVINGS','TERM_DEPOSIT')),
    CONSTRAINT chk_product_status CHECK (status IN ('ACTIVE','RETIRED'))
);

-- Extend account with product link, mandate and limits. Existing rows get sensible defaults.
ALTER TABLE account ADD COLUMN product_code VARCHAR(40);
ALTER TABLE account ADD COLUMN mandate_type VARCHAR(10) NOT NULL DEFAULT 'SINGLE';
ALTER TABLE account ADD COLUMN min_balance NUMERIC(19, 4) NOT NULL DEFAULT 0;
ALTER TABLE account ADD COLUMN daily_limit NUMERIC(19, 4) NOT NULL DEFAULT 0;
ALTER TABLE account ADD COLUMN overdraft_limit NUMERIC(19, 4) NOT NULL DEFAULT 0;
ALTER TABLE account ADD CONSTRAINT chk_account_mandate CHECK (mandate_type IN ('SINGLE','JOINT','ANY_ONE'));

CREATE TABLE account_holder (
    id          UUID        NOT NULL,
    account_id  UUID        NOT NULL,
    customer_id UUID        NOT NULL,
    role        VARCHAR(10) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_account_holder PRIMARY KEY (id),
    CONSTRAINT fk_account_holder_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT chk_account_holder_role CHECK (role IN ('PRIMARY','JOINT'))
);

CREATE INDEX idx_account_holder_account ON account_holder (account_id);
CREATE INDEX idx_account_holder_customer ON account_holder (customer_id);
