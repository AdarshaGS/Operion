-- A section's timetable is a draft until it is published. A row here means "published": created_at is
-- when it went out and created_by who published it; unpublishing deletes the row. Existing timetables
-- have no row, so they become drafts - publish them once to show them in the portals.

CREATE TABLE timetable_publications (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    section_id       BIGINT NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_timetable_publication_section UNIQUE (organisation_id, section_id),
    CONSTRAINT fk_timetable_publication_section FOREIGN KEY (section_id) REFERENCES sections (id)
) ENGINE = InnoDB;
