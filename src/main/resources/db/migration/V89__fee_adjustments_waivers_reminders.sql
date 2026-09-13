-- Adjustment and Waiver (#131): the two missing money-adjustment mechanisms alongside
-- the existing Refund - both additive/insert-only, referencing the Invoice they affect,
-- requiring a reason + approver, same shape as `refunds`. A standalone Credit ledger is
-- deliberately deferred (see #131's own "revisit deliberately" note) - filed as a
-- follow-up rather than built here.
CREATE TABLE adjustments (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    invoice_id       BIGINT NOT NULL,
    amount           DECIMAL(10, 2) NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    approved_by      BIGINT NOT NULL,
    adjustment_date  DATE NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_adjustments_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_adjustments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
) ENGINE = InnoDB;

CREATE INDEX idx_adjustments_invoice ON adjustments (invoice_id);

CREATE TABLE waivers (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    invoice_id       BIGINT NOT NULL,
    amount           DECIMAL(10, 2) NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    approved_by      BIGINT NOT NULL,
    waiver_date      DATE NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_waivers_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_waivers_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
) ENGINE = InnoDB;

CREATE INDEX idx_waivers_invoice ON waivers (invoice_id);

-- Permissions for the new mechanisms (#131) and the fee-reminder action (#237), following
-- the same closed/code-owned catalog convention as V10.
INSERT IGNORE INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('FEE_ADJUSTMENT_MANAGE', 'finance', 'Record a manual correction to what is owed on an invoice', NOW(6), NOW(6)),
    ('FEE_WAIVER_APPROVE',    'finance', 'Approve and record a waiver against an invoice',           NOW(6), NOW(6)),
    ('FEE_REMINDER_SEND',     'finance', 'Send an overdue-fee reminder to a student''s guardians',   NOW(6), NOW(6));
