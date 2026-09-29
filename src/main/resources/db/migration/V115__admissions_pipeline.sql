-- #217/#218/#219: public admission inquiry pipeline extends the existing applicants table
-- (Person-backed, converts to Student) rather than introducing a parallel inquiry concept.
-- Status gains UNDER_REVIEW (VARCHAR column, no schema change needed for the enum).
ALTER TABLE applicants
    ADD COLUMN desired_grade_id BIGINT NULL,
    ADD COLUMN guardian_name    VARCHAR(200) NULL,
    ADD COLUMN guardian_phone   VARCHAR(30) NULL,
    ADD COLUMN guardian_email   VARCHAR(200) NULL,
    ADD COLUMN rejection_reason VARCHAR(1000) NULL,
    ADD COLUMN reviewed_at      DATETIME(6) NULL,
    ADD CONSTRAINT fk_applicants_desired_grade FOREIGN KEY (desired_grade_id) REFERENCES grade_levels (id);

-- Staff-managed received-documents checklist (no public upload - abuse risk).
CREATE TABLE applicant_documents (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    applicant_id    BIGINT NOT NULL,
    doc_type        VARCHAR(50) NOT NULL,
    received        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT uq_applicant_documents_applicant_type UNIQUE (applicant_id, doc_type),
    CONSTRAINT fk_applicant_documents_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_applicant_documents_applicant FOREIGN KEY (applicant_id) REFERENCES applicants (id)
) ENGINE = InnoDB;
