-- V100 missed the created_by/updated_by columns BaseEntity (@CreatedBy/@LastModifiedBy)
-- requires on every entity, including a plain global catalog like this one - the same two
-- columns every other BaseEntity/TenantScopedEntity table already carries.
ALTER TABLE school_template_master_items
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL;
