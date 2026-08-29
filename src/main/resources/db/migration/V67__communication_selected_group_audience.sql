-- Backs AudienceType.SELECTED_GROUP (see its class doc): an ad-hoc, admin-chosen set of
-- Persons for one announcement, which doesn't fit the single audience_ref_id column
-- every other audience type resolves against.

CREATE TABLE announcement_audience_members (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    announcement_id   BIGINT NOT NULL,
    person_id         BIGINT NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_announcement_audience_members_announcement_person UNIQUE (announcement_id, person_id),
    CONSTRAINT fk_announcement_audience_members_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_announcement_audience_members_announcement FOREIGN KEY (announcement_id) REFERENCES announcements (id),
    CONSTRAINT fk_announcement_audience_members_person FOREIGN KEY (person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_announcement_audience_members_announcement ON announcement_audience_members (organisation_id, announcement_id);
