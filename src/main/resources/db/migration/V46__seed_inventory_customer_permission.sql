-- Customer management permission (GitHub #52), same reasoning as V44's
-- INVENTORY_SUPPLIER_MANAGE - a distinct catalog from items/categories/suppliers.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('INVENTORY_CUSTOMER_MANAGE', 'inventory', 'Create and manage store-sales customers', NOW(6), NOW(6));
