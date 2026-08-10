-- Library module schema: Book, BookCopy, BorrowRecord, Fine. See
-- ai-context/erp-system-plan.md §3.3 for the light sketch this deep-designed. Fine is
-- deliberately standalone - not wired into the Fees module's Invoice/Payment - per the
-- design sign-off: fine amounts are discretionary/school-specific and staff borrowers
-- have no StudentEnrollment to tie an invoice to.

CREATE TABLE books (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    isbn              VARCHAR(20),
    title             VARCHAR(200) NOT NULL,
    author            VARCHAR(200),
    publisher         VARCHAR(200),
    category          VARCHAR(100),
    edition           VARCHAR(50),
    status            VARCHAR(20) NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_books_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_books_isbn ON books (organisation_id, isbn);
CREATE INDEX idx_books_title ON books (organisation_id, title);

CREATE TABLE book_copies (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    book_id             BIGINT NOT NULL,
    campus_id           BIGINT NOT NULL,
    accession_number    VARCHAR(30) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    acquired_date       DATE,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_book_copies_org_accession UNIQUE (organisation_id, accession_number),
    CONSTRAINT fk_book_copies_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_book_copies_book FOREIGN KEY (book_id) REFERENCES books (id),
    CONSTRAINT fk_book_copies_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_book_copies_book_status ON book_copies (organisation_id, book_id, status);
CREATE INDEX idx_book_copies_campus_status ON book_copies (organisation_id, campus_id, status);

CREATE TABLE borrow_records (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id       BIGINT NOT NULL,
    book_copy_id          BIGINT NOT NULL,
    borrower_person_id    BIGINT NOT NULL,
    borrowed_date         DATE NOT NULL,
    due_date              DATE NOT NULL,
    returned_date         DATE,
    status                VARCHAR(20) NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT fk_borrow_records_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_borrow_records_book_copy FOREIGN KEY (book_copy_id) REFERENCES book_copies (id),
    CONSTRAINT fk_borrow_records_borrower FOREIGN KEY (borrower_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_borrow_records_copy_status ON borrow_records (organisation_id, book_copy_id, status);
CREATE INDEX idx_borrow_records_borrower_status ON borrow_records (organisation_id, borrower_person_id, status);

CREATE TABLE fines (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    borrow_record_id    BIGINT NOT NULL,
    amount              DECIMAL(10, 2) NOT NULL,
    reason              VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    paid_date           DATE,
    waived_by           BIGINT,
    waived_reason       VARCHAR(500),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_fines_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_fines_borrow_record FOREIGN KEY (borrow_record_id) REFERENCES borrow_records (id)
) ENGINE = InnoDB;

CREATE INDEX idx_fines_borrow_record ON fines (organisation_id, borrow_record_id);
CREATE INDEX idx_fines_status ON fines (organisation_id, status);
