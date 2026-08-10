-- Examination module permissions, following the same closed/code-owned catalog
-- convention as V2/V4/V6/V8/V10. No enforcement wired yet (none exists anywhere in the
-- codebase yet) - these rows exist so roles can be configured against them once
-- enforcement is built.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('EXAM_MANAGE',          'examination', 'Create and manage exams and exam schedules', NOW(6), NOW(6)),
    ('MARKS_ENTER',          'examination', 'Enter marks for an exam schedule',           NOW(6), NOW(6)),
    ('MARKS_CORRECT',        'examination', 'Correct an already-entered marks record',    NOW(6), NOW(6)),
    ('GRADING_SCALE_MANAGE', 'examination', 'Create and manage grading scales',           NOW(6), NOW(6)),
    ('REPORT_CARD_PUBLISH',  'examination', 'Publish a student report card',              NOW(6), NOW(6)),
    ('EXAM_VIEW',            'examination', 'View exams, schedules, marks, report cards', NOW(6), NOW(6));
