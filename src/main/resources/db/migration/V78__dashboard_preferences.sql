CREATE TABLE dashboard_preferences (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id             BIGINT NOT NULL,
    user_id                     BIGINT NOT NULL,
    setup_progress_dismissed    BOOLEAN NOT NULL DEFAULT FALSE,
    quick_actions_dismissed     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  DATETIME(6)  NOT NULL,
    updated_at                  DATETIME(6)  NOT NULL,
    created_by                  BIGINT,
    updated_by                  BIGINT,
    CONSTRAINT fk_dashboard_preferences_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT uq_dashboard_preferences_org_user UNIQUE (organisation_id, user_id)
) ENGINE = InnoDB;
