-- Student Management module permissions, following the same closed/code-owned catalog
-- convention as V2/V4. No enforcement wired yet (none exists anywhere in the codebase
-- yet) - these rows exist so roles can be configured against them once enforcement is
-- built.
INSERT IGNORE INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('STUDENT_VIEW',              'student', 'View student records',                        NOW(6), NOW(6)),
    ('STUDENT_MANAGE',            'student', 'Admit/edit student records',                   NOW(6), NOW(6)),
    ('STUDENT_ENROLLMENT_MANAGE', 'student', 'Enroll, promote, and reassign students',       NOW(6), NOW(6)),
    ('STUDENT_DOCUMENT_VIEW',     'student', 'View student documents',                       NOW(6), NOW(6)),
    ('STUDENT_DOCUMENT_MANAGE',   'student', 'Upload and verify student documents',          NOW(6), NOW(6)),
    ('STUDENT_EXIT_MANAGE',       'student', 'Record student transfers/withdrawals/exits',   NOW(6), NOW(6)),
    ('GUARDIAN_VIEW',             'parent',  'View guardians and student-guardian links',    NOW(6), NOW(6)),
    ('GUARDIAN_MANAGE',           'parent',  'Create guardians and manage student links',    NOW(6), NOW(6));
