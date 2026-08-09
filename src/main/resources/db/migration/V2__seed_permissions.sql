-- Global permission catalog. Closed and code-owned: tenants configure which
-- permissions a role has (role_permissions), never what permissions exist.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('STUDENT_VIEW',         'student',       'View student records',                       NOW(6), NOW(6)),
    ('STUDENT_CREATE',       'student',       'Create/admit students',                       NOW(6), NOW(6)),
    ('STUDENT_UPDATE',       'student',       'Update student records',                      NOW(6), NOW(6)),
    ('STUDENT_DELETE',       'student',       'Delete/deactivate student records',           NOW(6), NOW(6)),
    ('ATTENDANCE_VIEW',      'attendance',    'View attendance records',                     NOW(6), NOW(6)),
    ('ATTENDANCE_MARK',      'attendance',    'Mark or correct attendance',                  NOW(6), NOW(6)),
    ('FEE_VIEW',             'fees',          'View fee/invoice records',                    NOW(6), NOW(6)),
    ('FEE_COLLECT',          'fees',          'Record fee payments',                         NOW(6), NOW(6)),
    ('FEE_DISCOUNT_APPROVE', 'fees',          'Approve fee discounts/scholarships',          NOW(6), NOW(6)),
    ('REPORT_VIEW',          'reporting',     'View operational/academic reports',           NOW(6), NOW(6)),
    ('ROLE_MANAGE',          'authorization', 'Create/edit roles and their permissions',     NOW(6), NOW(6)),
    ('MEMBERSHIP_MANAGE',    'authorization', 'Grant/revoke organisation memberships',       NOW(6), NOW(6)),
    ('ORGANISATION_MANAGE',  'organisation',  'Manage organisation profile and configuration', NOW(6), NOW(6));
