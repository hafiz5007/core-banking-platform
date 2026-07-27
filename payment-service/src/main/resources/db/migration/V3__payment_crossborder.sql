-- Sprint 6: cross-border payments. Capture the FX conversion (rate + converted target amount) for
-- the beneficiary currency. These columns are null for domestic, same-currency payments.

ALTER TABLE payment ADD COLUMN target_currency_code VARCHAR(3);
ALTER TABLE payment ADD COLUMN target_amount NUMERIC(19, 4);
ALTER TABLE payment ADD COLUMN fx_rate NUMERIC(18, 8);
