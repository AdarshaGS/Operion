-- Reverses V54/V56's separate `reporting` MySQL database in favour of keeping everything
-- inside `operion` itself (deliberate simplification, confirmed after review - trades the
-- "physically separate database" guarantee for one fewer moving part: no second schema to
-- provision, no cross-schema table qualification). Views are renamed with a `reporting_`
-- prefix since they can't share a name with the real tables living in the same schema now
-- (e.g. a view can't be named `students` alongside the real `students` table). The
-- restricted `reporting_ro` role (V55) is re-granted per-view instead of via a schema-wide
-- wildcard - GRANT SELECT ON operion.* would expose every real table, not just these views.
--
-- Any previously-saved report whose SQL referenced the old `reporting.xxx` names needs its
-- query text updated to the new unqualified `reporting_xxx` names - not something a
-- migration can safely rewrite inside arbitrary user-authored SQL text.

DROP VIEW IF EXISTS reporting.purchase_orders;
DROP VIEW IF EXISTS reporting.inventory_stock;
DROP VIEW IF EXISTS reporting.sales;
DROP VIEW IF EXISTS reporting.staff;
DROP VIEW IF EXISTS reporting.exam_results;
DROP VIEW IF EXISTS reporting.fee_payments;
DROP VIEW IF EXISTS reporting.fee_invoices;
DROP VIEW IF EXISTS reporting.attendance_daily;
DROP VIEW IF EXISTS reporting.students;
DROP FUNCTION IF EXISTS reporting.current_org_id;
REVOKE SELECT ON reporting.* FROM 'reporting_ro'@'%';
DROP DATABASE IF EXISTS reporting;

-- Same MySQL restriction as before (a view's SELECT can't reference a user variable
-- directly, error 1351) - still worked around the same way, just relocated.
CREATE FUNCTION reporting_current_org_id() RETURNS BIGINT
    NO SQL DETERMINISTIC
BEGIN
    RETURN @reporting_org_id;
END;

CREATE VIEW reporting_students AS
SELECT
    s.id                                                        AS student_id,
    s.organisation_id                                           AS organisation_id,
    s.admission_number                                          AS admission_number,
    s.admission_date                                            AS admission_date,
    TRIM(CONCAT(p.first_name, ' ', COALESCE(p.last_name, '')))  AS student_name,
    p.gender                                                    AS gender,
    p.date_of_birth                                             AS date_of_birth,
    se.academic_year_id                                         AS academic_year_id,
    ay.name                                                     AS academic_year_name,
    COALESCE(sc.display_name, gl.name)                          AS class_name,
    sec.name                                                     AS section_name,
    se.roll_number                                              AS roll_number,
    s.status                                                    AS student_status
FROM students s
JOIN persons p ON p.id = s.person_id
LEFT JOIN student_enrollments se ON se.student_id = s.id AND se.is_current = TRUE
LEFT JOIN academic_years ay ON ay.id = se.academic_year_id
LEFT JOIN sections sec ON sec.id = se.section_id
LEFT JOIN school_classes sc ON sc.id = sec.school_class_id
LEFT JOIN grade_levels gl ON gl.id = sc.grade_level_id
WHERE s.organisation_id = reporting_current_org_id();

CREATE VIEW reporting_attendance_daily AS
SELECT
    sa.id                                                       AS attendance_id,
    sa.organisation_id                                          AS organisation_id,
    se.student_id                                               AS student_id,
    TRIM(CONCAT(p.first_name, ' ', COALESCE(p.last_name, ''))) AS student_name,
    sa.academic_year_id                                         AS academic_year_id,
    COALESCE(sc.display_name, gl.name)                          AS class_name,
    sec.name                                                     AS section_name,
    sa.attendance_date                                          AS attendance_date,
    sa.attendance_status                                        AS attendance_status,
    sa.is_excused                                               AS is_excused
FROM student_attendances sa
JOIN student_enrollments se ON se.id = sa.student_enrollment_id
JOIN students st ON st.id = se.student_id
JOIN persons p ON p.id = st.person_id
LEFT JOIN sections sec ON sec.id = sa.section_id
LEFT JOIN school_classes sc ON sc.id = sa.school_class_id
LEFT JOIN grade_levels gl ON gl.id = sc.grade_level_id
WHERE sa.organisation_id = reporting_current_org_id();

CREATE VIEW reporting_fee_invoices AS
SELECT
    i.id                                                        AS invoice_id,
    i.organisation_id                                           AS organisation_id,
    i.invoice_number                                            AS invoice_number,
    se.student_id                                               AS student_id,
    TRIM(CONCAT(p.first_name, ' ', COALESCE(p.last_name, ''))) AS student_name,
    i.academic_year_id                                          AS academic_year_id,
    ay.name                                                     AS academic_year_name,
    i.total_amount                                              AS total_amount,
    i.amount_paid                                               AS amount_paid,
    (i.total_amount - i.amount_paid)                            AS outstanding_amount,
    i.due_date                                                  AS due_date,
    i.status                                                    AS status
FROM invoices i
JOIN student_fee_assignments sfa ON sfa.id = i.student_fee_assignment_id
JOIN student_enrollments se ON se.id = sfa.student_enrollment_id
JOIN students st ON st.id = se.student_id
JOIN persons p ON p.id = st.person_id
LEFT JOIN academic_years ay ON ay.id = i.academic_year_id
WHERE i.organisation_id = reporting_current_org_id();

CREATE VIEW reporting_fee_payments AS
SELECT
    pay.id                                                       AS payment_id,
    pay.organisation_id                                          AS organisation_id,
    pay.receipt_number                                           AS receipt_number,
    pa.invoice_id                                                AS invoice_id,
    inv.invoice_number                                           AS invoice_number,
    se.student_id                                                AS student_id,
    TRIM(CONCAT(p.first_name, ' ', COALESCE(p.last_name, '')))  AS student_name,
    pay.academic_year_id                                         AS academic_year_id,
    pa.allocated_amount                                          AS allocated_amount,
    pay.amount                                                   AS payment_amount,
    pay.payment_method                                           AS payment_method,
    pay.payment_date                                             AS payment_date,
    pay.status                                                   AS status
FROM payments pay
JOIN payment_allocations pa ON pa.payment_id = pay.id
JOIN invoices inv ON inv.id = pa.invoice_id
JOIN student_fee_assignments sfa ON sfa.id = inv.student_fee_assignment_id
JOIN student_enrollments se ON se.id = sfa.student_enrollment_id
JOIN students st ON st.id = se.student_id
JOIN persons p ON p.id = st.person_id
WHERE pay.organisation_id = reporting_current_org_id();

CREATE VIEW reporting_exam_results AS
SELECT
    rc.id                                                        AS report_card_id,
    rc.organisation_id                                           AS organisation_id,
    e.id                                                         AS exam_id,
    e.name                                                       AS exam_name,
    e.exam_type                                                  AS exam_type,
    e.academic_year_id                                           AS academic_year_id,
    ay.name                                                      AS academic_year_name,
    se.student_id                                                AS student_id,
    TRIM(CONCAT(p.first_name, ' ', COALESCE(p.last_name, '')))  AS student_name,
    COALESCE(sc.display_name, gl.name)                          AS class_name,
    sec.name                                                      AS section_name,
    rc.total_marks_obtained                                      AS total_marks_obtained,
    rc.total_max_marks                                           AS total_max_marks,
    rc.percentage                                                AS percentage,
    rc.overall_grade                                             AS overall_grade
FROM report_cards rc
JOIN exams e ON e.id = rc.exam_id
JOIN student_enrollments se ON se.id = rc.student_enrollment_id
JOIN students st ON st.id = se.student_id
JOIN persons p ON p.id = st.person_id
LEFT JOIN academic_years ay ON ay.id = e.academic_year_id
LEFT JOIN sections sec ON sec.id = se.section_id
LEFT JOIN school_classes sc ON sc.id = sec.school_class_id
LEFT JOIN grade_levels gl ON gl.id = sc.grade_level_id
WHERE rc.organisation_id = reporting_current_org_id();

CREATE VIEW reporting_staff AS
SELECT
    sp.id                                                       AS staff_id,
    sp.organisation_id                                          AS organisation_id,
    sp.employee_code                                            AS employee_code,
    TRIM(CONCAT(p.first_name, ' ', COALESCE(p.last_name, ''))) AS staff_name,
    d.name                                                       AS designation_name,
    dept.name                                                    AS department_name,
    sp.employment_type                                          AS employment_type,
    sp.date_of_joining                                          AS date_of_joining,
    sp.status                                                   AS employment_status
FROM staff_profiles sp
JOIN persons p ON p.id = sp.person_id
JOIN designations d ON d.id = sp.designation_id
LEFT JOIN departments dept ON dept.id = sp.department_id
WHERE sp.organisation_id = reporting_current_org_id();

CREATE VIEW reporting_sales AS
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
FROM sales s
JOIN sale_lines sl ON sl.sale_id = s.id
JOIN items it ON it.id = sl.item_id
JOIN customers cust ON cust.id = s.customer_id
JOIN campuses camp ON camp.id = s.campus_id
WHERE s.organisation_id = reporting_current_org_id();

CREATE VIEW reporting_inventory_stock AS
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
    SELECT item_id, campus_id FROM stock_entries
    UNION
    SELECT item_id, campus_id FROM stock_issues
    UNION
    SELECT item_id, campus_id FROM stock_adjustments
) active
JOIN items it ON it.id = active.item_id
JOIN campuses camp ON camp.id = active.campus_id
LEFT JOIN (
    SELECT item_id, campus_id, SUM(quantity) AS total_received, SUM(quantity * COALESCE(unit_cost, 0)) AS total_received_cost
    FROM stock_entries GROUP BY item_id, campus_id
) e ON e.item_id = active.item_id AND e.campus_id = active.campus_id
LEFT JOIN (
    SELECT item_id, campus_id, SUM(quantity) AS total_issued
    FROM stock_issues GROUP BY item_id, campus_id
) iss ON iss.item_id = active.item_id AND iss.campus_id = active.campus_id
LEFT JOIN (
    SELECT item_id, campus_id, SUM(quantity_delta) AS total_adjusted
    FROM stock_adjustments GROUP BY item_id, campus_id
) adj ON adj.item_id = active.item_id AND adj.campus_id = active.campus_id
WHERE it.organisation_id = reporting_current_org_id();

CREATE VIEW reporting_purchase_orders AS
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
FROM purchase_orders po
JOIN purchase_order_lines pol ON pol.purchase_order_id = po.id
JOIN suppliers sup ON sup.id = po.supplier_id
JOIN campuses camp ON camp.id = po.campus_id
JOIN items it ON it.id = pol.item_id
WHERE po.organisation_id = reporting_current_org_id();

GRANT SELECT ON reporting_students TO 'reporting_ro'@'%';
GRANT SELECT ON reporting_attendance_daily TO 'reporting_ro'@'%';
GRANT SELECT ON reporting_fee_invoices TO 'reporting_ro'@'%';
GRANT SELECT ON reporting_fee_payments TO 'reporting_ro'@'%';
GRANT SELECT ON reporting_exam_results TO 'reporting_ro'@'%';
GRANT SELECT ON reporting_staff TO 'reporting_ro'@'%';
GRANT SELECT ON reporting_sales TO 'reporting_ro'@'%';
GRANT SELECT ON reporting_inventory_stock TO 'reporting_ro'@'%';
GRANT SELECT ON reporting_purchase_orders TO 'reporting_ro'@'%';
FLUSH PRIVILEGES;
