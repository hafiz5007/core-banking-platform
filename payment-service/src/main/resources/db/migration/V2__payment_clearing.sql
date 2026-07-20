-- Sprint 5: domestic clearing. Add the scheme reference and widen the status domain to include
-- the external-rail lifecycle states (SUBMITTED, SETTLED, RETURNED).

ALTER TABLE payment ADD COLUMN scheme_reference VARCHAR(60);

ALTER TABLE payment DROP CONSTRAINT chk_payment_status;
ALTER TABLE payment ADD CONSTRAINT chk_payment_status CHECK (status IN
    ('RECEIVED','VALIDATED','SCREENED','POSTED','SUBMITTED','SETTLED','CONFIRMED',
     'REJECTED','COMPENSATED','RETURNED'));
