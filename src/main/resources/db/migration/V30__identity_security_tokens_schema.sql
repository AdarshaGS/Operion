ALTER TABLE users ADD COLUMN email_verified_at DATETIME(6) NULL;

CREATE TABLE password_reset_tokens (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    user_id            BIGINT NOT NULL,
    token_hash         VARCHAR(255) NOT NULL,
    expires_at         DATETIME(6) NOT NULL,
    consumed           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT fk_password_reset_tokens_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX idx_password_reset_tokens_org_consumed ON password_reset_tokens (organisation_id, consumed);

CREATE TABLE email_verification_tokens (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    user_id            BIGINT NOT NULL,
    token_hash         VARCHAR(255) NOT NULL,
    expires_at         DATETIME(6) NOT NULL,
    consumed           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT fk_email_verification_tokens_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX idx_email_verification_tokens_org_consumed ON email_verification_tokens (organisation_id, consumed);

CREATE TABLE staff_invites (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    user_id            BIGINT NOT NULL,
    token_hash         VARCHAR(255) NOT NULL,
    expires_at         DATETIME(6) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT fk_staff_invites_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_staff_invites_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX idx_staff_invites_org_status ON staff_invites (organisation_id, status);
