-- Phone / email / payment-reference lookups (person directory search, duplicate checks, cheque re-presentation checks)
-- were full scans of an organisation's rows. Plain indexes only: none of these columns is unique
-- (families share a phone, re-presented cheques reuse a reference).
CREATE INDEX `idx_persons_org_phone` ON `persons` (`organisation_id`, `phone`);
CREATE INDEX `idx_persons_org_email` ON `persons` (`organisation_id`, `email`);
CREATE INDEX `idx_payments_org_reference` ON `payments` (`organisation_id`, `payment_reference`);
