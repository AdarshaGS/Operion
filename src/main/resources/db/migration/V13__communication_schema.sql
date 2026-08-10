-- Communication module schema: Announcement, NotificationTemplate, NotificationRecipient,
-- NotificationPreference. See ai-context/erp-system-plan.md §3.3 for the light sketch this
-- deep-designed. v1 ships IN_APP delivery only - EMAIL/SMS are reserved enum values, not
-- wired to any provider yet, so fan-out writes recipient rows straight to SENT (no
-- polling dispatch worker needed until an actual external channel exists).

CREATE TABLE announcements (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    campus_id         BIGINT,
    title             VARCHAR(200) NOT NULL,
    body              TEXT NOT NULL,
    audience_type     VARCHAR(20) NOT NULL,
    audience_ref_id   BIGINT,
    status            VARCHAR(20) NOT NULL,
    published_at      DATETIME(6),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_announcements_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_announcements_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_announcements_campus_status ON announcements (organisation_id, campus_id, status);

CREATE TABLE notification_templates (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    code               VARCHAR(50) NOT NULL,
    channel            VARCHAR(20) NOT NULL,
    subject_template   VARCHAR(200),
    body_template       TEXT NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT uq_notification_templates_org_code UNIQUE (organisation_id, code),
    CONSTRAINT fk_notification_templates_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE notification_recipients (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    announcement_id   BIGINT,
    person_id         BIGINT NOT NULL,
    channel           VARCHAR(20) NOT NULL,
    delivery_status   VARCHAR(20) NOT NULL,
    sent_at           DATETIME(6),
    read_at           DATETIME(6),
    failure_reason    VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_notification_recipients_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_notification_recipients_announcement FOREIGN KEY (announcement_id) REFERENCES announcements (id),
    CONSTRAINT fk_notification_recipients_person FOREIGN KEY (person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_notification_recipients_person_status ON notification_recipients (organisation_id, person_id, delivery_status);
CREATE INDEX idx_notification_recipients_status ON notification_recipients (organisation_id, delivery_status);

CREATE TABLE notification_preferences (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    person_id         BIGINT NOT NULL,
    channel           VARCHAR(20) NOT NULL,
    is_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_notification_preferences_person_channel UNIQUE (person_id, channel),
    CONSTRAINT fk_notification_preferences_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_notification_preferences_person FOREIGN KEY (person_id) REFERENCES persons (id)
) ENGINE = InnoDB;
