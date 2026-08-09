-- Academic module permissions, following the same closed/code-owned catalog convention
-- as V2. No enforcement wired yet (none exists anywhere in the codebase yet) - these
-- rows exist so roles can be configured against them once enforcement is built.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('GRADE_LEVEL_VIEW',        'academic', 'View grade level catalog',                  NOW(6), NOW(6)),
    ('GRADE_LEVEL_MANAGE',      'academic', 'Create/edit grade levels',                  NOW(6), NOW(6)),
    ('SUBJECT_VIEW',            'academic', 'View subject catalog',                      NOW(6), NOW(6)),
    ('SUBJECT_MANAGE',          'academic', 'Create/edit subjects',                      NOW(6), NOW(6)),
    ('CLASS_VIEW',              'academic', 'View classes and sections',                 NOW(6), NOW(6)),
    ('CLASS_MANAGE',            'academic', 'Create/edit classes and sections',          NOW(6), NOW(6)),
    ('TEACHER_ASSIGNMENT_VIEW', 'academic', 'View teacher assignments',                  NOW(6), NOW(6)),
    ('TEACHER_ASSIGNMENT_MANAGE', 'academic', 'Assign/reassign teachers to sections',    NOW(6), NOW(6));
