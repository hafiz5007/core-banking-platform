-- Sprint 3: double-entry general ledger.
-- Postings are immutable and append-only; corrections are made via reversal entries.

CREATE TABLE ledger_account (
    id            UUID         NOT NULL,
    code          VARCHAR(40)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    account_type  VARCHAR(20)  NOT NULL,
    currency_code VARCHAR(3)      NOT NULL,
    balance       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_ledger_account PRIMARY KEY (id),
    CONSTRAINT uq_ledger_account_code UNIQUE (code),
    CONSTRAINT chk_ledger_account_type CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE'))
);

CREATE TABLE journal_entry (
    id              UUID         NOT NULL,
    idempotency_key VARCHAR(80)  NOT NULL,
    narrative       VARCHAR(280) NOT NULL,
    value_date      DATE         NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    reversal_of_id  UUID,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_journal_entry PRIMARY KEY (id),
    CONSTRAINT uq_journal_entry_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_journal_entry_status CHECK (status IN ('POSTED','REVERSED'))
);

CREATE TABLE journal_line (
    id                UUID         NOT NULL,
    journal_entry_id  UUID         NOT NULL,
    ledger_account_id UUID         NOT NULL,
    account_code      VARCHAR(40)  NOT NULL,
    direction         VARCHAR(6)   NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    currency_code     VARCHAR(3)      NOT NULL,
    CONSTRAINT pk_journal_line PRIMARY KEY (id),
    CONSTRAINT fk_journal_line_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT fk_journal_line_account FOREIGN KEY (ledger_account_id) REFERENCES ledger_account (id),
    CONSTRAINT chk_journal_line_direction CHECK (direction IN ('DEBIT','CREDIT')),
    CONSTRAINT chk_journal_line_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_journal_line_entry ON journal_line (journal_entry_id);
CREATE INDEX idx_journal_line_account ON journal_line (ledger_account_id);
CREATE INDEX idx_journal_line_direction ON journal_line (direction);
