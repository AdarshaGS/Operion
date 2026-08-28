-- Curated, org-scoped read-only views for Custom Table Reports (GitHub #185). A separate
-- `reporting` schema (not just a set of views inside the app's own schema) so the
-- restricted DB role granted in V55 can be scoped to it with zero privileges on the real
-- application tables.
--
-- Each view filters on a session-scoped MySQL user variable, @reporting_org_id, that
-- ReportExecutionService sets (via `SET @reporting_org_id = ?`) on the same connection
-- immediately before running any report query. This makes org-scoping a property of the
-- view itself - whatever GROUP BY/JOIN/WHERE a report author's own SQL adds on top, it
-- physically cannot see another organisation's rows. It also fails closed: if the
-- variable is ever left unset (NULL), every view's WHERE clause evaluates to false and
-- returns zero rows rather than every organisation's data.
--
-- MySQL flatly refuses to let a view's SELECT reference a user variable directly
-- (error 1351, "View's SELECT contains a variable or parameter") - confirmed against a
-- real MySQL 8 instance while writing this migration, not a hypothetical. The standard
-- workaround, used here: wrap the variable read in a tiny NO SQL stored function and have
-- every view call that function instead of `@reporting_org_id` directly - MySQL only
-- checks the view's own text for a bare `@variable`, not what a function it calls does
-- internally.
--
-- ponytail: source tables are qualified with the literal `operion` schema name rather than
-- a configurable placeholder - this app has never run against a differently-named schema,
-- and MySQL resolves unqualified names in a view body against the view's OWN schema
-- (`reporting`), not the caller's, so qualification is required either way. If the schema
-- name in spring.datasource.url (DB_URL) is ever changed, these views must be recreated
-- (DROP VIEW + re-run the CREATE VIEW statements below) with the new name.

CREATE DATABASE IF NOT EXISTS reporting;

CREATE FUNCTION reporting.current_org_id() RETURNS BIGINT
    NO SQL DETERMINISTIC
BEGIN
    RETURN @reporting_org_id;
END;

CREATE VIEW reporting.students AS
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
FROM operion.students s
JOIN operion.persons p ON p.id = s.person_id
LEFT JOIN operion.student_enrollments se ON se.student_id = s.id AND se.is_current = TRUE
LEFT JOIN operion.academic_years ay ON ay.id = se.academic_year_id
LEFT JOIN operion.sections sec ON sec.id = se.section_id
LEFT JOIN operion.school_classes sc ON sc.id = sec.school_class_id
LEFT JOIN operion.grade_levels gl ON gl.id = sc.grade_level_id
WHERE s.organisation_id = reporting.current_org_id();

CREATE VIEW reporting.attendance_daily AS
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
FROM operion.student_attendances sa
JOIN operion.student_enrollments se ON se.id = sa.student_enrollment_id
JOIN operion.students st ON st.id = se.student_id
JOIN operion.persons p ON p.id = st.person_id
LEFT JOIN operion.sections sec ON sec.id = sa.section_id
LEFT JOIN operion.school_classes sc ON sc.id = sa.school_class_id
LEFT JOIN operion.grade_levels gl ON gl.id = sc.grade_level_id
WHERE sa.organisation_id = reporting.current_org_id();

CREATE VIEW reporting.fee_invoices AS
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
FROM operion.invoices i
JOIN operion.student_fee_assignments sfa ON sfa.id = i.student_fee_assignment_id
JOIN operion.student_enrollments se ON se.id = sfa.student_enrollment_id
JOIN operion.students st ON st.id = se.student_id
JOIN operion.persons p ON p.id = st.person_id
LEFT JOIN operion.academic_years ay ON ay.id = i.academic_year_id
WHERE i.organisation_id = reporting.current_org_id();

CREATE VIEW reporting.fee_payments AS
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
FROM operion.payments pay
JOIN operion.payment_allocations pa ON pa.payment_id = pay.id
JOIN operion.invoices inv ON inv.id = pa.invoice_id
JOIN operion.student_fee_assignments sfa ON sfa.id = inv.student_fee_assignment_id
JOIN operion.student_enrollments se ON se.id = sfa.student_enrollment_id
JOIN operion.students st ON st.id = se.student_id
JOIN operion.persons p ON p.id = st.person_id
WHERE pay.organisation_id = reporting.current_org_id();

CREATE VIEW reporting.exam_results AS
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
FROM operion.report_cards rc
JOIN operion.exams e ON e.id = rc.exam_id
JOIN operion.student_enrollments se ON se.id = rc.student_enrollment_id
JOIN operion.students st ON st.id = se.student_id
JOIN operion.persons p ON p.id = st.person_id
LEFT JOIN operion.academic_years ay ON ay.id = e.academic_year_id
LEFT JOIN operion.sections sec ON sec.id = se.section_id
LEFT JOIN operion.school_classes sc ON sc.id = sec.school_class_id
LEFT JOIN operion.grade_levels gl ON gl.id = sc.grade_level_id
WHERE rc.organisation_id = reporting.current_org_id();

CREATE VIEW reporting.staff AS
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
FROM operion.staff_profiles sp
JOIN operion.persons p ON p.id = sp.person_id
JOIN operion.designations d ON d.id = sp.designation_id
LEFT JOIN operion.departments dept ON dept.id = sp.department_id
WHERE sp.organisation_id = reporting.current_org_id();
