CREATE TABLE student_transfer_requests (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    student_id          BIGINT NOT NULL,
    from_campus_id      BIGINT NOT NULL,
    to_campus_id        BIGINT NOT NULL,
    reason              VARCHAR(500),
    status              VARCHAR(20) NOT NULL,
    requested_by        BIGINT NOT NULL,
    decided_by          BIGINT,
    decided_at          DATETIME(6),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_student_transfer_requests_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_student_transfer_requests_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_student_transfer_requests_from_campus FOREIGN KEY (from_campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_student_transfer_requests_to_campus FOREIGN KEY (to_campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_student_transfer_requests_student_status ON student_transfer_requests (organisation_id, student_id, status);

-- Student module permission, following the same closed/code-owned catalog convention as V6.
INSERT IGNORE INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('STUDENT_TRANSFER_MANAGE', 'student', 'Raise, approve, and reject intra-org campus transfer requests', NOW(6), NOW(6));
