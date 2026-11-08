-- Admission-fee clearance (phase 1: state and workflow, nothing is blocked yet).
--
-- 1. student_enrollments gets a stored fee_clearance_status. Every existing row (and every row created by a
--    promotion or a CSV import) is NOT_REQUIRED, so current students are grandfathered; only the new
--    enrol-then-invoice workflow ever writes PENDING / CLEARED / EXEMPTED.
-- 2. invoices.admission_invoice marks the invoices that make up a student's admission payment, so a fee assigned
--    later (e.g. transport) never retroactively becomes part of the admission requirement.
-- 3. fee_categories.required_at_admission: only structures of a category flagged here are assigned and invoiced
--    when a student is enrolled.
-- 4. admission_settings: one row per organisation (created lazily on first PUT, OFF when absent).
-- 5. FEE_ADMISSION_CLEARANCE_OVERRIDE permission - seeded to no role (owners bypass permission checks).

ALTER TABLE student_enrollments
    ADD COLUMN fee_clearance_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN fee_clearance_changed_at DATETIME(6) NULL,
    ADD COLUMN fee_clearance_override_by BIGINT NULL,
    ADD COLUMN fee_clearance_override_reason VARCHAR(500) NULL,
    ADD INDEX idx_student_enrollments_org_fee_clearance (organisation_id, fee_clearance_status);

ALTER TABLE invoices
    ADD COLUMN admission_invoice BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE fee_categories
    ADD COLUMN required_at_admission BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE admission_settings (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    fee_clearance_mode  VARCHAR(20) NOT NULL DEFAULT 'OFF',
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_admission_settings_organisation UNIQUE (organisation_id)
) ENGINE = InnoDB;

INSERT IGNORE INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('FEE_ADMISSION_CLEARANCE_OVERRIDE', 'finance', 'Override a student''s pending admission-fee clearance with a recorded reason', NOW(6), NOW(6));
