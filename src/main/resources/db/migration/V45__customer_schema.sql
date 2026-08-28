-- Store-sales customer master data (GitHub #52), ahead of the Sales module (Milestone 7)
-- that will consume it. student_id/guardian_id are both nullable and mutually exclusive
-- (enforced in CustomerController) - a walk-in customer has neither set.
CREATE TABLE customers (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    student_id        BIGINT,
    guardian_id       BIGINT,
    name              VARCHAR(200) NOT NULL,
    phone             VARCHAR(30),
    status            VARCHAR(20) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_customers_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_customers_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_customers_guardian FOREIGN KEY (guardian_id) REFERENCES guardians (id)
) ENGINE = InnoDB;

CREATE INDEX idx_customers_org_status ON customers (organisation_id, status);
