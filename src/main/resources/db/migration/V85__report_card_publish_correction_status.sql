-- Correction-after-publish (#138): a ReportCard now carries a status (PUBLISHED /
-- SUPERSEDED - "supersede, don't silently overwrite", same convention as
-- student_fee_assignments.status) and a stale flag, set when a correction is made to
-- marks behind an already-published report card.

-- The original (exam_id, student_enrollment_id) uniqueness (V11) assumed one row ever
-- existed per pair; the supersede pattern now needs multiple rows over time (one
-- PUBLISHED, any number SUPERSEDED), enforced at the app layer instead - same as
-- student_fee_assignments (V9), which has no equivalent DB-level constraint either.
-- The plain replacement index must exist before the unique one is dropped - MySQL still
-- needs an index on these columns to support the exam_id/student_enrollment_id foreign keys.
CREATE INDEX idx_report_cards_exam_enrollment ON report_cards (exam_id, student_enrollment_id);

ALTER TABLE report_cards
    DROP INDEX uq_report_cards_exam_enrollment,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN stale BOOLEAN NOT NULL DEFAULT FALSE;

-- Separate, higher-trust permission for correcting marks after a report card has already
-- been published - same reasoning as ATTENDANCE_UNLOCK (V81) and MARKS_SUBMIT/MARKS_APPROVE
-- (V83): ordinary MARKS_CORRECT shouldn't automatically grant this.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('MARKS_CORRECT_AFTER_PUBLISH', 'examination',
     'Correct marks after a report card has already been published, flagging it stale', NOW(6), NOW(6));
