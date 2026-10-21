-- Ready-made school reports (fee collection, fee defaulters, attendance %, exam results, subject marks)
-- run through the saved-report engine on the org-scoped reporting views. The existing views lacked
-- what a school groups and reads by - admission number, class/section, fee head, collector, card
-- status/rank - so they are extended here (new columns appended, existing ones unchanged), and
-- per-subject marks get a view of their own.

CREATE OR REPLACE VIEW `reporting_fee_invoices` AS
SELECT i.id AS invoice_id, i.organisation_id AS organisation_id, i.invoice_number AS invoice_number,
       se.student_id AS student_id, TRIM(CONCAT(p.first_name, ' ', COALESCE(p.last_name, ''))) AS student_name,
       i.academic_year_id AS academic_year_id, ay.name AS academic_year_name,
       i.total_amount AS total_amount, i.amount_paid AS amount_paid, (i.total_amount - i.amount_paid) AS outstanding_amount,
       i.due_date AS due_date, i.status AS status,
       st.admission_number AS admission_number, COALESCE(sc.display_name, gl.name) AS class_name, sec.name AS section_name,
       fc.name AS fee_head, se.id AS student_enrollment_id, st.status AS student_status
FROM invoices i
JOIN student_fee_assignments sfa ON sfa.id = i.student_fee_assignment_id
JOIN student_enrollments se ON se.id = sfa.student_enrollment_id
JOIN students st ON st.id = se.student_id
JOIN persons p ON p.id = st.person_id
LEFT JOIN academic_years ay ON ay.id = i.academic_year_id
LEFT JOIN sections sec ON sec.id = se.section_id
LEFT JOIN school_classes sc ON sc.id = sec.school_class_id
LEFT JOIN grade_levels gl ON gl.id = sc.grade_level_id
LEFT JOIN fee_structures fs ON fs.id = sfa.fee_structure_id
LEFT JOIN fee_categories fc ON fc.id = fs.fee_category_id
WHERE i.organisation_id = reporting_current_org_id();

CREATE OR REPLACE VIEW `reporting_fee_payments` AS
SELECT pay.id AS payment_id, pay.organisation_id AS organisation_id, pay.receipt_number AS receipt_number,
       pa.invoice_id AS invoice_id, inv.invoice_number AS invoice_number,
       se.student_id AS student_id, TRIM(CONCAT(p.first_name, ' ', COALESCE(p.last_name, ''))) AS student_name,
       pay.academic_year_id AS academic_year_id, pa.allocated_amount AS allocated_amount, pay.amount AS payment_amount,
       pay.payment_method AS payment_method, pay.payment_date AS payment_date, pay.status AS status,
       st.admission_number AS admission_number, COALESCE(sc.display_name, gl.name) AS class_name, sec.name AS section_name,
       fc.name AS fee_head, u.email AS collected_by, pay.payment_reference AS payment_reference
FROM payments pay
JOIN payment_allocations pa ON pa.payment_id = pay.id
JOIN invoices inv ON inv.id = pa.invoice_id
JOIN student_fee_assignments sfa ON sfa.id = inv.student_fee_assignment_id
JOIN student_enrollments se ON se.id = sfa.student_enrollment_id
JOIN students st ON st.id = se.student_id
JOIN persons p ON p.id = st.person_id
LEFT JOIN sections sec ON sec.id = se.section_id
LEFT JOIN school_classes sc ON sc.id = sec.school_class_id
LEFT JOIN grade_levels gl ON gl.id = sc.grade_level_id
LEFT JOIN fee_structures fs ON fs.id = sfa.fee_structure_id
LEFT JOIN fee_categories fc ON fc.id = fs.fee_category_id
LEFT JOIN users u ON u.id = pay.created_by
WHERE pay.organisation_id = reporting_current_org_id();

CREATE OR REPLACE VIEW `reporting_attendance_daily` AS
SELECT sa.id AS attendance_id, sa.organisation_id AS organisation_id, se.student_id AS student_id,
       TRIM(CONCAT(p.first_name, ' ', COALESCE(p.last_name, ''))) AS student_name, sa.academic_year_id AS academic_year_id,
       COALESCE(sc.display_name, gl.name) AS class_name, sec.name AS section_name,
       sa.attendance_date AS attendance_date, sa.attendance_status AS attendance_status, sa.is_excused AS is_excused,
       st.admission_number AS admission_number, se.roll_number AS roll_number
FROM student_attendances sa
JOIN student_enrollments se ON se.id = sa.student_enrollment_id
JOIN students st ON st.id = se.student_id
JOIN persons p ON p.id = st.person_id
LEFT JOIN sections sec ON sec.id = sa.section_id
LEFT JOIN school_classes sc ON sc.id = sa.school_class_id
LEFT JOIN grade_levels gl ON gl.id = sc.grade_level_id
WHERE sa.organisation_id = reporting_current_org_id();

CREATE OR REPLACE VIEW `reporting_exam_results` AS
SELECT rc.id AS report_card_id, rc.organisation_id AS organisation_id, e.id AS exam_id, e.name AS exam_name,
       e.exam_type AS exam_type, e.academic_year_id AS academic_year_id, ay.name AS academic_year_name,
       se.student_id AS student_id, TRIM(CONCAT(p.first_name, ' ', COALESCE(p.last_name, ''))) AS student_name,
       COALESCE(sc.display_name, gl.name) AS class_name, sec.name AS section_name,
       rc.total_marks_obtained AS total_marks_obtained, rc.total_max_marks AS total_max_marks,
       rc.percentage AS percentage, rc.overall_grade AS overall_grade,
       st.admission_number AS admission_number, rc.passed AS passed, rc.class_rank AS class_rank,
       rc.status AS status, rc.stale AS stale
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

-- One row per student per exam subject. register_status says whether the marks are still a draft.
CREATE OR REPLACE VIEW `reporting_exam_marks` AS
SELECT me.id AS marks_entry_id, me.organisation_id AS organisation_id, e.id AS exam_id, e.name AS exam_name,
       ay.name AS academic_year_name, COALESCE(sc.display_name, gl.name) AS class_name, sec.name AS section_name,
       se.student_id AS student_id, TRIM(CONCAT(p.first_name, ' ', COALESCE(p.last_name, ''))) AS student_name,
       st.admission_number AS admission_number, se.roll_number AS roll_number,
       sub.name AS subject_name, es.max_marks AS max_marks, es.pass_marks AS pass_marks,
       me.marks_obtained AS marks_obtained, me.is_absent AS is_absent,
       CASE WHEN me.is_absent THEN 'ABSENT' WHEN me.marks_obtained >= es.pass_marks THEN 'PASS' ELSE 'FAIL' END AS result,
       COALESCE(mer.register_status, 'DRAFT') AS register_status
FROM marks_entries me
JOIN exam_schedules es ON es.id = me.exam_schedule_id
JOIN exams e ON e.id = es.exam_id
JOIN subjects sub ON sub.id = es.subject_id
JOIN student_enrollments se ON se.id = me.student_enrollment_id
JOIN students st ON st.id = se.student_id
JOIN persons p ON p.id = st.person_id
LEFT JOIN marks_entry_registers mer ON mer.exam_schedule_id = es.id
LEFT JOIN academic_years ay ON ay.id = e.academic_year_id
LEFT JOIN sections sec ON sec.id = se.section_id
LEFT JOIN school_classes sc ON sc.id = sec.school_class_id
LEFT JOIN grade_levels gl ON gl.id = sc.grade_level_id
WHERE me.organisation_id = reporting_current_org_id();

GRANT SELECT ON `reporting_exam_marks` TO 'reporting_ro'@'%';
