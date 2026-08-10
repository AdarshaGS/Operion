-- Communication module permissions, following the same closed/code-owned catalog
-- convention as V2/V4/V6/V8/V10/V12. No enforcement wired yet (none exists anywhere in
-- the codebase yet) - these rows exist so roles can be configured against them once
-- enforcement is built.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('ANNOUNCEMENT_CREATE',          'communication', 'Create a draft announcement',                     NOW(6), NOW(6)),
    ('ANNOUNCEMENT_PUBLISH',         'communication', 'Publish an announcement and fan out to its audience', NOW(6), NOW(6)),
    ('ANNOUNCEMENT_CANCEL',          'communication', 'Cancel a draft announcement',                     NOW(6), NOW(6)),
    ('NOTIFICATION_TEMPLATE_MANAGE', 'communication', 'Create and manage notification templates',        NOW(6), NOW(6)),
    ('COMMUNICATION_VIEW',           'communication', 'View announcements and notifications',            NOW(6), NOW(6));
