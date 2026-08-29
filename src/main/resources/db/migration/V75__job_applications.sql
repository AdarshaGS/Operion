CREATE TABLE job_applications (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    applicant_name      VARCHAR(255) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    specialization      VARCHAR(255),
    years_experience    INT,
    status              VARCHAR(20) NOT NULL,
    applied_at          DATETIME(6)  NOT NULL,
    decided_by          BIGINT,
    decided_at          DATETIME(6),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_job_applications_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_job_applications_status ON job_applications (organisation_id, status);

-- HR module permission, following the same closed/code-owned catalog convention as V22.
INSERT IGNORE INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('HR_RECRUITMENT_MANAGE', 'hr', 'Review, approve, and reject job applications', NOW(6), NOW(6));
