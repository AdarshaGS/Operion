-- Supplier management permission (GitHub #50), same closed/code-owned catalog
-- convention as V20's inventory permissions - kept separate from
-- INVENTORY_CATALOG_MANAGE (items/categories) since suppliers are a distinct catalog.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('INVENTORY_SUPPLIER_MANAGE', 'inventory', 'Create and manage suppliers', NOW(6), NOW(6));
