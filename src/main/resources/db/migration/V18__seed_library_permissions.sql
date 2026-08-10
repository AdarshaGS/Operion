-- Library module permissions, following the same closed/code-owned catalog convention
-- as V2/V4/V6/V8/V10/V12/V14/V16. No enforcement wired yet (none exists anywhere in the
-- codebase yet) - these rows exist so roles can be configured against them once
-- enforcement is built.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('LIBRARY_CATALOG_MANAGE', 'library', 'Create and manage books and book copies',       NOW(6), NOW(6)),
    ('LIBRARY_BORROW_MANAGE',  'library', 'Issue, return, and mark book copies lost',      NOW(6), NOW(6)),
    ('LIBRARY_FINE_MANAGE',    'library', 'Raise, pay, and waive fines',                   NOW(6), NOW(6)),
    ('LIBRARY_VIEW',           'library', 'View books, copies, borrow records, and fines', NOW(6), NOW(6));
