CREATE TABLE refresh_tokens (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    user_id            BIGINT NOT NULL,
    token_hash         VARCHAR(255) NOT NULL,
    expires_at         DATETIME(6) NOT NULL,
    revoked            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT fk_refresh_tokens_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX idx_refresh_tokens_org_revoked ON refresh_tokens (organisation_id, revoked);
