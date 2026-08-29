CREATE TABLE profile_change_requests (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    person_id           BIGINT NOT NULL,
    phone               VARCHAR(50),
    email               VARCHAR(255),
    photo_url           VARCHAR(500),
    status              VARCHAR(20) NOT NULL,
    requested_by        BIGINT NOT NULL,
    reviewed_by         BIGINT,
    reviewed_at         DATETIME(6),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_profile_change_requests_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_profile_change_requests_person FOREIGN KEY (person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_profile_change_requests_person_status ON profile_change_requests (organisation_id, person_id, status);

-- Identity module permission, following the same closed/code-owned catalog convention as V6.
INSERT IGNORE INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('PROFILE_CHANGE_MANAGE', 'identity', 'Review, approve, and reject self-service profile change requests', NOW(6), NOW(6));
