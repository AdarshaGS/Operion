-- Vehicle document tracking (insurance, permit, fitness/pollution certificates) with
-- expiry and verification, per #156. Same shape as staff_documents/student_documents -
-- re-upload supersedes the prior ACTIVE row of the same type rather than overwriting it.

CREATE TABLE vehicle_documents (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id       BIGINT NOT NULL,
    vehicle_id            BIGINT NOT NULL,
    document_type         VARCHAR(50) NOT NULL,
    file_reference        VARCHAR(500) NOT NULL,
    file_name             VARCHAR(255) NOT NULL,
    mime_type             VARCHAR(100),
    expiry_date           DATE,
    verification_status   VARCHAR(20) NOT NULL,
    verified_by           BIGINT,
    verified_at           DATETIME(6),
    status                VARCHAR(20) NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT fk_vehicle_documents_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_vehicle_documents_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id)
) ENGINE = InnoDB;

CREATE INDEX idx_vehicle_documents_vehicle_status ON vehicle_documents (organisation_id, vehicle_id, status);
