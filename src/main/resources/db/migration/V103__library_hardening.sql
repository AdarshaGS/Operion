-- Library milestone hardening (#167/#168/#169/#170/#171): shelf location per physical
-- copy (BookCopy is campus-scoped, so this is more accurate than putting it on Book -
-- see #167), and org-configurable borrowing policy mirroring examination_settings'
-- lazy-row-per-organisation shape.

ALTER TABLE book_copies
    ADD COLUMN shelf_location VARCHAR(255) NULL;

CREATE TABLE library_settings (
    id                             BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id                BIGINT NOT NULL,
    max_loans_student              INT NOT NULL DEFAULT 3,
    max_loans_staff                INT NOT NULL DEFAULT 5,
    borrowing_period_days_student  INT NOT NULL DEFAULT 14,
    borrowing_period_days_staff    INT NOT NULL DEFAULT 30,
    block_issue_on_overdue         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                     DATETIME(6) NOT NULL,
    updated_at                     DATETIME(6) NOT NULL,
    created_by                     BIGINT,
    updated_by                     BIGINT,
    CONSTRAINT uq_library_settings_organisation UNIQUE (organisation_id),
    CONSTRAINT fk_library_settings_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;
