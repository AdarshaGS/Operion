-- Org-wide admin-bypass permission (GitHub #200), assignable to any role via the
-- existing Role -> Permission chain - unlike OrganisationMembership.isOwner (a single
-- boolean tied to one fixed membership, see V37), this lets an org create additional
-- "full access" roles of its own. Checked in PermissionInterceptor alongside the
-- existing Owner bypass, never enumerated onto Owner itself (Owner already bypasses
-- every check unconditionally and needs no catalog row to do it).
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('ALL_FUNCTIONS', 'authorization', 'Bypasses every permission-gated action - an admin-equivalent grant usable by any role', NOW(6), NOW(6));
