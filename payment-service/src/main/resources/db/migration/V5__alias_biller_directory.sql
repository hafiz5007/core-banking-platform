-- Sprint 9: P2P alias directory and biller directory.

CREATE TABLE payment_alias (
    id           UUID         NOT NULL,
    alias        VARCHAR(120) NOT NULL,
    alias_type   VARCHAR(10)  NOT NULL,
    account_code VARCHAR(40)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_payment_alias PRIMARY KEY (id),
    CONSTRAINT uq_payment_alias UNIQUE (alias),
    CONSTRAINT chk_alias_type CHECK (alias_type IN ('PHONE','EMAIL'))
);

CREATE TABLE biller (
    id                 UUID         NOT NULL,
    biller_code        VARCHAR(40)  NOT NULL,
    name               VARCHAR(120) NOT NULL,
    settlement_account VARCHAR(40)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_biller PRIMARY KEY (id),
    CONSTRAINT uq_biller_code UNIQUE (biller_code)
);
