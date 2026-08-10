-- Fee module permissions, following the same closed/code-owned catalog convention as
-- V2/V4/V6/V8. No enforcement wired yet (none exists anywhere in the codebase yet) -
-- these rows exist so roles can be configured against them once enforcement is built.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('FEE_CATEGORY_MANAGE',  'finance', 'Create and manage fee categories',               NOW(6), NOW(6)),
    ('FEE_STRUCTURE_MANAGE', 'finance', 'Create and manage fee structures/installments',  NOW(6), NOW(6)),
    ('FEE_ASSIGNMENT_MANAGE','finance', 'Assign fee structures to student enrollments',   NOW(6), NOW(6)),
    ('FEE_DISCOUNT_APPROVE', 'finance', 'Approve a discount on a student fee assignment', NOW(6), NOW(6)),
    ('FEE_INVOICE_MANAGE',   'finance', 'Generate invoices',                              NOW(6), NOW(6)),
    ('FEE_COLLECT',          'finance', 'Record payments against invoices',               NOW(6), NOW(6)),
    ('FEE_REFUND_APPROVE',   'finance', 'Approve and record refunds',                     NOW(6), NOW(6)),
    ('FEE_VIEW',             'finance', 'View fee structures, invoices, and payments',    NOW(6), NOW(6));
