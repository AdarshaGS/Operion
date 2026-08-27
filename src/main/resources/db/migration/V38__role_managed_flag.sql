-- Decouples parent-portal access from a role literally named "Guardian" (GitHub #91).
-- is_managed marks a role the platform provisions/protects for a specific system flow,
-- distinct from is_system_default (the org's fallback admin role).
ALTER TABLE roles
    ADD COLUMN is_managed BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: any org that already has a "Guardian" role from the old DefaultRoles seeding
-- gets it marked managed, so PortalInviteService's new findFirstByManaged(true) lookup
-- reuses that existing row instead of creating a second, duplicate "Guardian" role the
-- next time a portal invite is claimed in that org.
UPDATE roles SET is_managed = TRUE WHERE name = 'Guardian' AND is_system_default = FALSE;
