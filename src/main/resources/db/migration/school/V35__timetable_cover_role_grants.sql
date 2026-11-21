-- Leave-cover (substitution) endpoints are gated by TIMETABLE_VIEW / TIMETABLE_MANAGE. Grant them to the
-- starter-pack roles that run cover: Principal gets VIEW + MANAGE, HR Manager gets VIEW only.
--
-- 1. Existing organisations' roles: same backfill shape as V109 / V39, matched by role name. A school that has
--    no such role (or renamed it) simply matches no rows, and rows already present are skipped.
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id
    FROM roles r
    JOIN permissions p ON p.code IN ('TIMETABLE_VIEW', 'TIMETABLE_MANAGE')
    WHERE r.name = 'Principal'
      AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id
    FROM roles r
    JOIN permissions p ON p.code = 'TIMETABLE_VIEW'
    WHERE r.name = 'HR Manager'
      AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 2. The role templates (permission_codes is a comma-separated list, same as V100): the global master catalog
--    that new organisations copy from, and every organisation's already-copied rows, so applying the starter
--    pack gives the same grants.
UPDATE school_template_master_items
    SET permission_codes = CONCAT(permission_codes, ',TIMETABLE_VIEW')
    WHERE category = 'ROLE' AND name IN ('Principal', 'HR Manager')
      AND permission_codes IS NOT NULL AND FIND_IN_SET('TIMETABLE_VIEW', permission_codes) = 0;
UPDATE school_template_master_items
    SET permission_codes = CONCAT(permission_codes, ',TIMETABLE_MANAGE')
    WHERE category = 'ROLE' AND name = 'Principal'
      AND permission_codes IS NOT NULL AND FIND_IN_SET('TIMETABLE_MANAGE', permission_codes) = 0;

UPDATE school_template_items
    SET permission_codes = CONCAT(permission_codes, ',TIMETABLE_VIEW')
    WHERE category = 'ROLE' AND name IN ('Principal', 'HR Manager')
      AND permission_codes IS NOT NULL AND FIND_IN_SET('TIMETABLE_VIEW', permission_codes) = 0;
UPDATE school_template_items
    SET permission_codes = CONCAT(permission_codes, ',TIMETABLE_MANAGE')
    WHERE category = 'ROLE' AND name = 'Principal'
      AND permission_codes IS NOT NULL AND FIND_IN_SET('TIMETABLE_MANAGE', permission_codes) = 0;
