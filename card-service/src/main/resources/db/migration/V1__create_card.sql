-- Sprint 8: card and authorization tables. The PAN is never stored — only a token and last four.

CREATE TABLE card (
    id                UUID         NOT NULL,
    card_token        VARCHAR(60)  NOT NULL,
    last_four         CHAR(4)      NOT NULL,
    account_code      VARCHAR(40)  NOT NULL,
    currency_code     CHAR(3)      NOT NULL,
    status            VARCHAR(12)  NOT NULL,
    available_balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_card PRIMARY KEY (id),
    CONSTRAINT uq_card_token UNIQUE (card_token),
    CONSTRAINT chk_card_status CHECK (status IN ('REQUESTED','ACTIVE','BLOCKED','EXPIRED'))
);

CREATE INDEX idx_card_account ON card (account_code);

CREATE TABLE card_authorization (
    id            UUID         NOT NULL,
    card_id       UUID         NOT NULL,
    amount        NUMERIC(19, 4) NOT NULL,
    currency_code CHAR(3)      NOT NULL,
    merchant      VARCHAR(120) NOT NULL,
    status        VARCHAR(10)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_card_authorization PRIMARY KEY (id),
    CONSTRAINT fk_card_authorization_card FOREIGN KEY (card_id) REFERENCES card (id),
    CONSTRAINT chk_authorization_status CHECK (status IN ('APPROVED','DECLINED','SETTLED','REVERSED'))
);

CREATE INDEX idx_card_authorization_card ON card_authorization (card_id);
