-- HR module permissions, following the same closed/code-owned catalog convention as
-- V2/V4/V6/V8/V10/V12/V14/V16/V18/V20. No enforcement wired yet (none exists anywhere
-- in the codebase yet) - these rows exist so roles can be configured against them once
-- enforcement is built.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('HR_STAFF_MANAGE',      'hr', 'Create and manage staff profiles and documents', NOW(6), NOW(6)),
    ('HR_LEAVE_TYPE_MANAGE', 'hr', 'Create leave types and allocate leave balances',  NOW(6), NOW(6)),
    ('HR_LEAVE_MANAGE',      'hr', 'Raise, approve, reject, and cancel leave requests', NOW(6), NOW(6)),
    ('HR_VIEW',              'hr', 'View staff profiles, leave balances, and requests', NOW(6), NOW(6));
