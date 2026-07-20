-- Sprint 8 (cont.): customer card usage controls.

ALTER TABLE card ADD COLUMN online_enabled        BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE card ADD COLUMN contactless_enabled   BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE card ADD COLUMN atm_enabled           BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE card ADD COLUMN international_enabled  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE card ADD COLUMN per_transaction_limit NUMERIC(19, 4) NOT NULL DEFAULT 0;
