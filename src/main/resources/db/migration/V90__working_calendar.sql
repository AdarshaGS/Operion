-- Working calendar settings (#143): a holidays / special-working-days list scoped to an
-- academic year and optionally a single campus (null campus_id = organisation-wide).
CREATE TABLE working_calendar_entries (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    academic_year_id BIGINT NOT NULL,
    campus_id        BIGINT NULL,
    entry_date       DATE NOT NULL,
    label            VARCHAR(150) NOT NULL,
    type             VARCHAR(30) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_working_calendar_entries_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_working_calendar_entries_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_working_calendar_entries_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_working_calendar_entries_organisation ON working_calendar_entries (organisation_id);
CREATE INDEX idx_working_calendar_entries_academic_year ON working_calendar_entries (academic_year_id);

INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('ACADEMIC_CONFIGURATION_VIEW',   'academic', 'View school timings (start/end time)',          NOW(6), NOW(6)),
    ('ACADEMIC_CONFIGURATION_MANAGE', 'academic', 'Edit school timings (start/end time)',           NOW(6), NOW(6)),
    ('WORKING_CALENDAR_VIEW',         'academic', 'View holidays and special working days',         NOW(6), NOW(6)),
    ('WORKING_CALENDAR_MANAGE',       'academic', 'Add/remove holidays and special working days',   NOW(6), NOW(6));

-- Same backfill shape as V39 (MEMBERSHIP_VIEW): every existing org's system-default
-- (Owner) role gets the new codes too, matching what a freshly-provisioned org's Owner
-- gets via OrganisationService.seedDefaultRoles().
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id
    FROM roles r
    CROSS JOIN permissions p
    WHERE r.is_system_default = TRUE
      AND p.code IN ('ACADEMIC_CONFIGURATION_VIEW', 'ACADEMIC_CONFIGURATION_MANAGE', 'WORKING_CALENDAR_VIEW', 'WORKING_CALENDAR_MANAGE');
