-- Times a teacher cannot be given a timetable slot (a part-time day off, a fixed commitment elsewhere).
-- period_id NULL means the whole weekday. The timetable refuses to place the teacher then, and the
-- generator and substitute suggestions steer around it. Existing timetables are not touched.

CREATE TABLE teacher_unavailability (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    teacher_person_id  BIGINT NOT NULL,
    day_of_week        VARCHAR(10) NOT NULL,
    period_id          BIGINT NULL,
    reason             VARCHAR(255) NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT fk_unavailability_teacher FOREIGN KEY (teacher_person_id) REFERENCES persons (id),
    CONSTRAINT fk_unavailability_period FOREIGN KEY (period_id) REFERENCES periods (id)
) ENGINE = InnoDB;

CREATE INDEX idx_unavailability_teacher ON teacher_unavailability (organisation_id, teacher_person_id);
