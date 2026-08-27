-- Splits member-list visibility from MEMBERSHIP_MANAGE (GitHub #98), so a role can be
-- granted read-only visibility into who's a member without also being able to add/revoke.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('MEMBERSHIP_VIEW', 'authorization', 'View organisation membership list', NOW(6), NOW(6));

-- Backfill: any role that could see the member list yesterday (via MEMBERSHIP_MANAGE)
-- keeps being able to today, now via the new, narrower code.
INSERT INTO role_permissions (role_id, permission_id)
    SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'MEMBERSHIP_VIEW')
    FROM role_permissions rp
    JOIN permissions p ON p.id = rp.permission_id
    WHERE p.code = 'MEMBERSHIP_MANAGE';
