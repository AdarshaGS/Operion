-- One-day cover for a timetable slot: on this date, this teacher takes the slot instead of the one it
-- belongs to (absent, on leave). The weekly timetable itself is never changed. At most one substitute
-- per slot per date. Rows are removed with their slot, or when the slot's teacher changes.

CREATE TABLE timetable_substitutions (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id             BIGINT NOT NULL,
    timetable_entry_id          BIGINT NOT NULL,
    substitution_date           DATE NOT NULL,
    absent_teacher_person_id    BIGINT NOT NULL,
    substitute_teacher_person_id BIGINT NOT NULL,
    reason                      VARCHAR(255) NULL,
    created_at                  DATETIME(6) NOT NULL,
    updated_at                  DATETIME(6) NOT NULL,
    created_by                  BIGINT,
    updated_by                  BIGINT,
    CONSTRAINT uq_substitution_slot_date UNIQUE (organisation_id, timetable_entry_id, substitution_date),
    CONSTRAINT fk_substitution_entry FOREIGN KEY (timetable_entry_id) REFERENCES timetable_entries (id),
    CONSTRAINT fk_substitution_absent FOREIGN KEY (absent_teacher_person_id) REFERENCES persons (id),
    CONSTRAINT fk_substitution_substitute FOREIGN KEY (substitute_teacher_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_substitution_substitute_date ON timetable_substitutions (organisation_id, substitute_teacher_person_id, substitution_date);
CREATE INDEX idx_substitution_date ON timetable_substitutions (organisation_id, substitution_date);
