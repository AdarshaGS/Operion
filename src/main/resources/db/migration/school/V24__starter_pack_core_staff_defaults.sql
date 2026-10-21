-- Phase 1 defaults (AppModule.defaultEnabled): Dashboard, Students, Academics, Attendance,
-- Examinations, Fees, Communication (announcements) and HR start on; every other module is
-- optional and starts off. The Starter Pack pre-ticks the same set, so a school that accepts
-- the defaults ends up where a school with no settings at all would be.

-- Reports leaves the baseline: it is optional now, so it gets its own question.
UPDATE school_starter_needs SET module_codes = 'DASHBOARD,STUDENTS,ACADEMICS,ATTENDANCE' WHERE need_key = 'BASELINE';

-- Announcements are core; staff chat (Messaging) is optional and gets its own question.
UPDATE school_starter_needs
SET module_codes = 'COMMUNICATION', default_yes = 1, label = 'Do you send announcements and notices through Operion?'
WHERE need_key = 'COMMUNICATION';

UPDATE school_starter_needs SET default_yes = 1 WHERE need_key IN ('FEES', 'EXAMS', 'HR');
UPDATE school_starter_needs SET default_yes = 0
WHERE need_key IN ('LEARNING', 'TRANSPORT', 'LIBRARY', 'PAYROLL', 'INVENTORY', 'PROCUREMENT', 'COMPLIANCE');

INSERT INTO school_starter_needs (need_key, label, help_text, default_yes, baseline, display_order, module_codes, created_at, updated_at) VALUES
('MESSAGING', 'Do staff chat with each other inside Operion?', NULL, 0, 0, 45, 'MESSAGING', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
('PERFORMANCE', 'Do you track teacher KPIs, goals and appraisals?', 'Needs HR and Academics.', 0, 0, 75, 'PERFORMANCE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
('REPORTS', 'Do you want saved custom reports?', 'Saved reports on curated data views, shared by user or role.', 0, 0, 120, 'REPORTS', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
