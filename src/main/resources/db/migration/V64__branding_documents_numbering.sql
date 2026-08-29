-- Branding & Documents settings (#142) - footer text plus configurable numbering-format
-- templates for admission/invoice/receipt numbers, extending the branding row added in
-- V61. Defaults reproduce today's hardcoded formats exactly so existing numbers keep
-- their shape until an admin changes them.
ALTER TABLE organisation_branding
    ADD COLUMN footer_text             VARCHAR(500),
    ADD COLUMN admission_number_format VARCHAR(100) NOT NULL DEFAULT 'STU-{YYYY}-{SEQ:4}',
    ADD COLUMN invoice_number_format   VARCHAR(100) NOT NULL DEFAULT 'INV-{AY}-{SEQ:6}',
    ADD COLUMN receipt_number_format   VARCHAR(100) NOT NULL DEFAULT 'RCT-{AY}-{SEQ:6}';

-- One row per (organisation, calendar year) - mirrors fee_document_counters' per-year
-- reset, keyed by calendar year (not academic year) since a Student has no AcademicYear
-- link at admission time, only an admissionDate.
CREATE TABLE student_admission_counters (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    calendar_year     INT NOT NULL,
    next_number       BIGINT NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_student_admission_counters_year UNIQUE (organisation_id, calendar_year),
    CONSTRAINT fk_student_admission_counters_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;
