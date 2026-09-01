-- ExamSchedule can now optionally be scoped to a single section within a class (staggered
-- exam date/room per section), not just the whole class. Nullable section_id preserves
-- today's "applies to every section" behavior as the default. The old (exam, class,
-- subject) uniqueness is widened to include section_id; MySQL treats NULLs as distinct
-- so a whole-class row and a per-section row for the same subject don't collide at the DB
-- level - ExaminationService.addSchedule() rejects that conflict at the app layer. Per #139.

ALTER TABLE exam_schedules
    ADD COLUMN section_id BIGINT NULL AFTER school_class_id,
    ADD CONSTRAINT fk_exam_schedules_section FOREIGN KEY (section_id) REFERENCES sections (id);

ALTER TABLE exam_schedules
    DROP INDEX uq_exam_schedules_exam_class_subject,
    ADD CONSTRAINT uq_exam_schedules_exam_class_subject_section UNIQUE (exam_id, school_class_id, subject_id, section_id);
