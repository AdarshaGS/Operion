-- Purchase module permissions (GitHub #56), same two-tier VIEW/MANAGE split named
-- explicitly in the ticket - unlike Inventory's finer-grained MANAGE split, Purchase
-- doesn't separate submit/approve/receive/return into their own codes since no
-- separation-of-duties requirement was raised for this module.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('PURCHASE_VIEW',   'purchase', 'View purchase orders, lines, and returns', NOW(6), NOW(6)),
    ('PURCHASE_MANAGE', 'purchase', 'Create, submit, approve, cancel, and receive purchase orders; record purchase returns', NOW(6), NOW(6));
