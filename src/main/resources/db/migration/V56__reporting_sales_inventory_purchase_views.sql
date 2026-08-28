-- Three more curated views backing the seeded Sales/Inventory/Purchase reports (GitHub
-- #66-#68). These weren't in V54's original six (#185 named only students/attendance/fees/
-- exam/staff) - needed because the chosen engine design (#185-190) only ever executes
-- against curated `reporting.*` views, never the app's live tables directly, so seeding a
-- Sales or Purchase report requires a view for it to read from. GRANT SELECT ON
-- reporting.* from V55 already covers these - a schema-level wildcard grant applies to
-- objects created later too, no re-grant needed.

CREATE VIEW reporting.sales AS
SELECT
    s.id                              AS sale_id,
    s.organisation_id                 AS organisation_id,
    s.receipt_number                  AS receipt_number,
    s.sale_date                       AS sale_date,
    camp.name                         AS campus_name,
    cust.name                         AS customer_name,
    sl.item_id                        AS item_id,
    it.name                           AS item_name,
    sl.quantity                       AS quantity,
    sl.unit_price                     AS unit_price,
    (sl.quantity * sl.unit_price)     AS line_total,
    s.total_amount                    AS sale_total_amount,
    s.amount_paid                     AS sale_amount_paid,
    s.status                          AS status
FROM operion.sales s
JOIN operion.sale_lines sl ON sl.sale_id = s.id
JOIN operion.items it ON it.id = sl.item_id
JOIN operion.customers cust ON cust.id = s.customer_id
JOIN operion.campuses camp ON camp.id = s.campus_id
WHERE s.organisation_id = reporting.current_org_id();

-- Balance = SUM(stock_entries.quantity) - SUM(stock_issues.quantity) +
-- SUM(stock_adjustments.quantity_delta), grouped by (item_id, campus_id) - the exact
-- arithmetic InventoryService.getBalance() uses, replicated here rather than
-- approximated. Pre-aggregating each ledger table separately (not a plain three-way
-- JOIN) avoids double-counting when an item/campus has multiple rows in more than one
-- table. Only (item_id, campus_id) pairs with at least one ledger row appear - an item
-- never stocked at a campus doesn't get a phantom all-zero row.
CREATE VIEW reporting.inventory_stock AS
SELECT
    it.id                              AS item_id,
    it.organisation_id                 AS organisation_id,
    it.code                            AS item_code,
    it.name                            AS item_name,
    it.reorder_level                   AS reorder_level,
    camp.id                            AS campus_id,
    camp.name                          AS campus_name,
    COALESCE(e.total_received, 0)      AS quantity_received,
    COALESCE(iss.total_issued, 0)      AS quantity_issued,
    COALESCE(adj.total_adjusted, 0)    AS quantity_adjusted,
    (COALESCE(e.total_received, 0) - COALESCE(iss.total_issued, 0) + COALESCE(adj.total_adjusted, 0)) AS balance,
    COALESCE(e.total_received_cost, 0) AS received_cost
FROM (
    SELECT item_id, campus_id FROM operion.stock_entries
    UNION
    SELECT item_id, campus_id FROM operion.stock_issues
    UNION
    SELECT item_id, campus_id FROM operion.stock_adjustments
) active
JOIN operion.items it ON it.id = active.item_id
JOIN operion.campuses camp ON camp.id = active.campus_id
LEFT JOIN (
    SELECT item_id, campus_id, SUM(quantity) AS total_received, SUM(quantity * COALESCE(unit_cost, 0)) AS total_received_cost
    FROM operion.stock_entries GROUP BY item_id, campus_id
) e ON e.item_id = active.item_id AND e.campus_id = active.campus_id
LEFT JOIN (
    SELECT item_id, campus_id, SUM(quantity) AS total_issued
    FROM operion.stock_issues GROUP BY item_id, campus_id
) iss ON iss.item_id = active.item_id AND iss.campus_id = active.campus_id
LEFT JOIN (
    SELECT item_id, campus_id, SUM(quantity_delta) AS total_adjusted
    FROM operion.stock_adjustments GROUP BY item_id, campus_id
) adj ON adj.item_id = active.item_id AND adj.campus_id = active.campus_id
WHERE it.organisation_id = reporting.current_org_id();

-- Row grain is one purchase order LINE, not one PO - lets a report GROUP BY supplier/
-- item/status freely. No stored PO or line total anywhere in the schema (confirmed
-- against V47/V49) - line_amount is always derived as quantity * unit_cost.
CREATE VIEW reporting.purchase_orders AS
SELECT
    po.id                              AS purchase_order_id,
    po.organisation_id                 AS organisation_id,
    sup.name                           AS supplier_name,
    camp.name                          AS campus_name,
    po.expected_date                   AS expected_date,
    po.status                          AS status,
    pol.item_id                        AS item_id,
    it.name                            AS item_name,
    pol.quantity                       AS quantity_ordered,
    pol.quantity_received              AS quantity_received,
    pol.unit_cost                      AS unit_cost,
    (pol.quantity * pol.unit_cost)     AS line_amount
FROM operion.purchase_orders po
JOIN operion.purchase_order_lines pol ON pol.purchase_order_id = po.id
JOIN operion.suppliers sup ON sup.id = po.supplier_id
JOIN operion.campuses camp ON camp.id = po.campus_id
JOIN operion.items it ON it.id = pol.item_id
WHERE po.organisation_id = reporting.current_org_id();
