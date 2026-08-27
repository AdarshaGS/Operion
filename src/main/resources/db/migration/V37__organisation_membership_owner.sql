-- First-class Organisation Owner (GitHub #90). is_owner is the real "*" capability
-- PermissionInterceptor bypasses granular checks for - distinct from roles.is_system_default,
-- which stays a plain NOT NULL FK filler role from here on.
ALTER TABLE organisation_memberships
    ADD COLUMN is_owner BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: exactly one Owner per organisation - the earliest-created ACTIVE membership
-- holding that org's system-default role (the only membership that could have plausibly
-- provisioned the org, since seedDefaultRoles() has only ever created one such role per org).
UPDATE organisation_memberships m
    JOIN (
        SELECT ranked.id FROM (
            SELECT m2.id, ROW_NUMBER() OVER (PARTITION BY m2.organisation_id ORDER BY m2.id) AS rn
            FROM organisation_memberships m2
            JOIN roles r2 ON r2.id = m2.role_id AND r2.is_system_default = TRUE
            WHERE m2.status = 'ACTIVE'
        ) ranked
        WHERE ranked.rn = 1
    ) earliest ON earliest.id = m.id
    SET m.is_owner = TRUE;

-- Cosmetic rename to match the model: the system-default role backing the Owner's
-- membership is no longer displayed as "Org Admin".
UPDATE roles SET name = 'Owner' WHERE name = 'Org Admin' AND is_system_default = TRUE;
