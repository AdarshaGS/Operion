-- Inventory module permissions, following the same closed/code-owned catalog convention
-- as V2/V4/V6/V8/V10/V12/V14/V16/V18. No enforcement wired yet (none exists anywhere in
-- the codebase yet) - these rows exist so roles can be configured against them once
-- enforcement is built. INVENTORY_ADJUSTMENT_MANAGE is kept separate from
-- INVENTORY_STOCK_MANAGE - adjustments are write-offs (damage/loss), same
-- separation-of-duties precedent as FEE_DISCOUNT_APPROVE vs FEE_COLLECT.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('INVENTORY_CATALOG_MANAGE',    'inventory', 'Create and manage item categories and items', NOW(6), NOW(6)),
    ('INVENTORY_STOCK_MANAGE',      'inventory', 'Record stock entries and issues',              NOW(6), NOW(6)),
    ('INVENTORY_ADJUSTMENT_MANAGE', 'inventory', 'Raise stock adjustments (damage/loss/count correction)', NOW(6), NOW(6)),
    ('INVENTORY_VIEW',              'inventory', 'View items, stock ledger entries, and balances', NOW(6), NOW(6));
