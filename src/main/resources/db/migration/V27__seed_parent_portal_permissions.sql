-- Parent portal permission, following the same closed/code-owned catalog convention as
-- V2/V4/V6/V8/V10/V12/V14/V16/V18/V20/V22. Granted to the new "Guardian" default role
-- (see DefaultRoles.java) - a fresh org gets it seeded automatically; an org provisioned
-- before this existed needs a Guardian role created manually via Settings > Roles.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('PARENT_PORTAL_ACCESS', 'parent', 'Log into the parent portal and view own children''s data', NOW(6), NOW(6));
