-- Register of official documents issued (fee receipts first; certificates, report cards and hall
-- tickets later). data_snapshot freezes the printed values at first issue so reprints never drift;
-- the PDF itself is re-rendered on demand, never stored.

CREATE TABLE issued_documents (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    document_kind    VARCHAR(30) NOT NULL,
    document_number  VARCHAR(60) NOT NULL,
    subject_type     VARCHAR(30) NOT NULL,
    subject_id       BIGINT NOT NULL,
    data_snapshot    TEXT NOT NULL,
    content_hash     CHAR(64) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    print_count      INT NOT NULL DEFAULT 0,
    last_printed_at  DATETIME(6) NULL,
    last_printed_by  BIGINT NULL,
    cancelled_at     DATETIME(6) NULL,
    cancel_reason    VARCHAR(255) NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_issued_documents_number UNIQUE (organisation_id, document_kind, document_number)
) ENGINE = InnoDB;

CREATE INDEX idx_issued_documents_subject ON issued_documents (organisation_id, document_kind, subject_type, subject_id);
