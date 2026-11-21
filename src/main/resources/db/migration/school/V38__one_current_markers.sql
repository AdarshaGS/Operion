-- "At most one current / primary / active row" rules that until now lived only in service code, as unique keys.
--
-- A plain UNIQUE (scope, is_current) cannot express "one TRUE per scope" (it would also allow only one FALSE), so each
-- table gets a nullable marker column that is 1 while the row is the current/primary/active one and NULL otherwise.
-- MySQL (and H2) allow repeated NULLs in a unique index, so UNIQUE (scope, marker) allows exactly one marked row per
-- scope. The application keeps the marker in step with the flag it mirrors (entity mutators); the original
-- is_current / is_primary_guardian / status columns stay the source of truth for reads.
--
-- academic_years and student_enrollments are NOT de-duplicated here: two current rows would mean the data is already
-- wrong in a way that needs a person to decide which one is right, so the ADD UNIQUE KEY fails and the migration stops
-- (run the Phase 0 detection queries in docs/stabilisation/DATA_MODEL_PLAN.md first). MySQL DDL is not transactional:
-- if it fails, drop the marker column it had already added on that table before fixing the data and re-running.

-- student_guardians: more than one ACTIVE primary per student -> keep the lowest id as primary, demote the rest.
UPDATE `student_guardians` sg
JOIN (
  SELECT `student_id`, MIN(`id`) AS keep_id
  FROM `student_guardians`
  WHERE `is_primary_guardian` = 1 AND `status` = 'ACTIVE'
  GROUP BY `student_id`
  HAVING COUNT(*) > 1
) d ON d.`student_id` = sg.`student_id`
SET sg.`is_primary_guardian` = 0
WHERE sg.`is_primary_guardian` = 1 AND sg.`status` = 'ACTIVE' AND sg.`id` <> d.keep_id;

-- student_transport_assignments: more than one ACTIVE row per enrollment -> keep the highest id (the latest), end the rest.
UPDATE `student_transport_assignments` sta
JOIN (
  SELECT `student_enrollment_id`, MAX(`id`) AS keep_id
  FROM `student_transport_assignments`
  WHERE `status` = 'ACTIVE'
  GROUP BY `student_enrollment_id`
  HAVING COUNT(*) > 1
) d ON d.`student_enrollment_id` = sta.`student_enrollment_id`
SET sta.`status` = 'ENDED', sta.`effective_to` = CURDATE()
WHERE sta.`status` = 'ACTIVE' AND sta.`id` <> d.keep_id;

-- academic_years: one current year per organisation.
ALTER TABLE `academic_years` ADD COLUMN `current_marker` tinyint(1) DEFAULT NULL;
UPDATE `academic_years` SET `current_marker` = IF(`is_current` = 1, 1, NULL);
ALTER TABLE `academic_years` ADD UNIQUE KEY `uq_academic_years_org_current` (`organisation_id`, `current_marker`);

-- student_enrollments: one current enrollment per student.
ALTER TABLE `student_enrollments` ADD COLUMN `current_marker` tinyint(1) DEFAULT NULL;
UPDATE `student_enrollments` SET `current_marker` = IF(`is_current` = 1, 1, NULL);
ALTER TABLE `student_enrollments` ADD UNIQUE KEY `uq_student_enrollments_student_current` (`student_id`, `current_marker`);

-- student_guardians: one ACTIVE primary guardian per student.
ALTER TABLE `student_guardians` ADD COLUMN `primary_marker` tinyint(1) DEFAULT NULL;
UPDATE `student_guardians` SET `primary_marker` = IF(`is_primary_guardian` = 1 AND `status` = 'ACTIVE', 1, NULL);
ALTER TABLE `student_guardians` ADD UNIQUE KEY `uq_student_guardians_student_primary` (`student_id`, `primary_marker`);

-- student_transport_assignments: one ACTIVE assignment per enrollment.
ALTER TABLE `student_transport_assignments` ADD COLUMN `active_marker` tinyint(1) DEFAULT NULL;
UPDATE `student_transport_assignments` SET `active_marker` = IF(`status` = 'ACTIVE', 1, NULL);
ALTER TABLE `student_transport_assignments` ADD UNIQUE KEY `uq_student_transport_assignments_enrollment_active` (`student_enrollment_id`, `active_marker`);
