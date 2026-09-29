-- #206: self-service portal routing needs a generic, tenant-configurable signal for
-- "this role is a portal-only role" - not a hardcoded role name, since roles are fully
-- org-configurable. Defaults false so every existing role keeps landing in the admin
-- shell unchanged.
ALTER TABLE roles
    ADD COLUMN is_portal BOOLEAN NOT NULL DEFAULT FALSE;
