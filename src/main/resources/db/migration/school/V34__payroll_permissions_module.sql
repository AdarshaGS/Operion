-- PAYROLL_VIEW / PAYROLL_MANAGE were seeded under module 'hr', but AppModule.forPermissionGroup has a
-- dedicated "payroll" group. With the module still 'hr', switching the Payroll module off left these
-- permissions listed on roles (the permission picker and RoleResponse hide by module group).
UPDATE permissions SET module = 'payroll' WHERE code IN ('PAYROLL_VIEW', 'PAYROLL_MANAGE');
