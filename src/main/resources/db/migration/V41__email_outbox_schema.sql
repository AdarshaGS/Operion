-- Transactional email delivery outbox (GitHub #105) - one row per send attempt for
-- member invites / email verification, recording which provider (brevo/resend) actually
-- delivered it or why both failed. See com.operion.email.EmailOutbox.
CREATE TABLE email_outbox (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    recipient          VARCHAR(255) NOT NULL,
    subject            VARCHAR(255) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    provider           VARCHAR(20),
    sent_at            DATETIME(6),
    failure_reason     VARCHAR(500),
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT fk_email_outbox_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_email_outbox_org_status ON email_outbox (organisation_id, status);
