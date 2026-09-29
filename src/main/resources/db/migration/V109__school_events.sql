-- Ad-hoc school events calendar (#240): sports day, PTM, etc. - admin-added, distinct from
-- WorkingCalendarEntry's holiday/special-working-day data (#143). Single-date only for v1.
CREATE TABLE school_events (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    campus_id        BIGINT NULL,
    event_date       DATE NOT NULL,
    title            VARCHAR(150) NOT NULL,
    description      TEXT,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_school_events_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_school_events_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_school_events_organisation ON school_events (organisation_id);
CREATE INDEX idx_school_events_date ON school_events (event_date);

INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('SCHOOL_EVENT_VIEW',   'academic', 'View the school events calendar',        NOW(6), NOW(6)),
    ('SCHOOL_EVENT_MANAGE', 'academic', 'Add/remove ad-hoc school events',        NOW(6), NOW(6));

-- Same backfill shape as V90 (WORKING_CALENDAR_*): every existing org's system-default
-- (Owner) role gets the new codes too, matching what a freshly-provisioned org's Owner
-- gets via OrganisationService.seedDefaultRoles().
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id
    FROM roles r
    CROSS JOIN permissions p
    WHERE r.is_system_default = TRUE
      AND p.code IN ('SCHOOL_EVENT_VIEW', 'SCHOOL_EVENT_MANAGE');
