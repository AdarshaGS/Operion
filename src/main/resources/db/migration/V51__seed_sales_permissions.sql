-- Sales module permissions (GitHub #60-64), same two-tier VIEW/MANAGE split as Purchase -
-- no separation-of-duties requirement raised for create-vs-record-payment.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('SALES_VIEW',   'sales', 'View sales, receipts, and customer purchase history', NOW(6), NOW(6)),
    ('SALES_MANAGE', 'sales', 'Create sales and record payments against them', NOW(6), NOW(6));
