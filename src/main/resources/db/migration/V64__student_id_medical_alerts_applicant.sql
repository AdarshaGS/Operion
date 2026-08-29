-- #114: system-generated student ID (distinct from the user-typed admission_number),
-- medical alerts, and a standalone emergency contact (not tied to a linked guardian).
ALTER TABLE students
    ADD COLUMN student_id              VARCHAR(30),
    ADD COLUMN medical_alerts          TEXT,
    ADD COLUMN emergency_contact_name  VARCHAR(150),
    ADD COLUMN emergency_contact_phone VARCHAR(30);

-- Backfill existing rows so student_id can go NOT NULL/UNIQUE - mirrors the real
-- generator's STU-{year}-{sequence} shape using each row's own admission year, so
-- backfilled values stay recognisable rather than an arbitrary placeholder.
UPDATE students
SET student_id = CONCAT('STU-', YEAR(admission_date), '-', LPAD(id, 5, '0'))
WHERE student_id IS NULL;

ALTER TABLE students
    MODIFY COLUMN student_id VARCHAR(30) NOT NULL,
    ADD CONSTRAINT uq_students_student_id UNIQUE (student_id);

CREATE TABLE student_id_counters (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    counter_year      INT NOT NULL,
    next_number       BIGINT NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_student_id_counters_org_year UNIQUE (organisation_id, counter_year),
    CONSTRAINT fk_student_id_counters_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

-- #114: "applicant" (pre-admission inquiry) is a separate, lightweight entity rather
-- than a Student status - admission_number/admission_date are NOT NULL on students and
-- would be meaningless for a prospect never actually admitted. A Person still backs it
-- (same identity table everyone else in the org uses), so converting to a Student on
-- admission needs no name/DOB/gender re-entry.
CREATE TABLE applicants (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    person_id     BIGINT NOT NULL,
    inquiry_date  DATE NOT NULL,
    source        VARCHAR(100),
    notes         TEXT,
    status        VARCHAR(20) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    created_by    BIGINT,
    updated_by    BIGINT,
    CONSTRAINT fk_applicants_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_applicants_person FOREIGN KEY (person_id) REFERENCES persons (id)
) ENGINE = InnoDB;
