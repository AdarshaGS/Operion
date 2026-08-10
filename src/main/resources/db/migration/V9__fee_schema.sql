-- Fee module schema: FeeCategory, FeeStructure, FeeStructureInstallment,
-- StudentFeeAssignment, Invoice, Payment, PaymentAllocation, Refund,
-- FeeDocumentCounter. See ai-context/erp-system-plan.md §3.2 for the design.
-- Money is never edited/deleted - a wrong payment is bounced, a wrong collection is
-- refunded, both additive reversing events.

CREATE TABLE fee_categories (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    code             VARCHAR(50) NOT NULL,
    name             VARCHAR(100) NOT NULL,
    description      VARCHAR(500),
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_fee_categories_org_code UNIQUE (organisation_id, code),
    CONSTRAINT fk_fee_categories_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE fee_structures (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    academic_year_id  BIGINT NOT NULL,
    school_class_id   BIGINT NOT NULL,
    fee_category_id   BIGINT NOT NULL,
    amount            DECIMAL(10, 2) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_fee_structures_year_class_category UNIQUE (organisation_id, academic_year_id, school_class_id, fee_category_id),
    CONSTRAINT fk_fee_structures_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_fee_structures_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_fee_structures_school_class FOREIGN KEY (school_class_id) REFERENCES school_classes (id),
    CONSTRAINT fk_fee_structures_fee_category FOREIGN KEY (fee_category_id) REFERENCES fee_categories (id)
) ENGINE = InnoDB;

CREATE TABLE fee_structure_installments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    fee_structure_id    BIGINT NOT NULL,
    installment_number  INT NOT NULL,
    due_date            DATE NOT NULL,
    amount              DECIMAL(10, 2) NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_fee_structure_installments_structure_number UNIQUE (fee_structure_id, installment_number),
    CONSTRAINT fk_fee_structure_installments_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_fee_structure_installments_structure FOREIGN KEY (fee_structure_id) REFERENCES fee_structures (id)
) ENGINE = InnoDB;

CREATE TABLE student_fee_assignments (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id        BIGINT NOT NULL,
    student_enrollment_id  BIGINT NOT NULL,
    fee_structure_id       BIGINT NOT NULL,
    base_amount            DECIMAL(10, 2) NOT NULL,
    discount_amount        DECIMAL(10, 2) NOT NULL DEFAULT 0,
    effective_amount       DECIMAL(10, 2) NOT NULL,
    discount_reason        VARCHAR(500),
    approved_by            BIGINT,
    status                 VARCHAR(20)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    created_by             BIGINT,
    updated_by             BIGINT,
    CONSTRAINT fk_student_fee_assignments_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_student_fee_assignments_enrollment FOREIGN KEY (student_enrollment_id) REFERENCES student_enrollments (id),
    CONSTRAINT fk_student_fee_assignments_structure FOREIGN KEY (fee_structure_id) REFERENCES fee_structures (id)
) ENGINE = InnoDB;

CREATE INDEX idx_student_fee_assignments_enrollment ON student_fee_assignments (student_enrollment_id);

CREATE TABLE invoices (
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id               BIGINT NOT NULL,
    academic_year_id              BIGINT NOT NULL,
    student_fee_assignment_id     BIGINT NOT NULL,
    fee_structure_installment_id  BIGINT NOT NULL,
    invoice_number                VARCHAR(50) NOT NULL,
    total_amount                  DECIMAL(10, 2) NOT NULL,
    amount_paid                   DECIMAL(10, 2) NOT NULL DEFAULT 0,
    due_date                      DATE NOT NULL,
    status                        VARCHAR(20)  NOT NULL,
    created_at                    DATETIME(6)  NOT NULL,
    updated_at                    DATETIME(6)  NOT NULL,
    created_by                    BIGINT,
    updated_by                    BIGINT,
    CONSTRAINT uq_invoices_assignment_installment UNIQUE (student_fee_assignment_id, fee_structure_installment_id),
    CONSTRAINT uq_invoices_org_number UNIQUE (organisation_id, invoice_number),
    CONSTRAINT fk_invoices_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_invoices_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_invoices_assignment FOREIGN KEY (student_fee_assignment_id) REFERENCES student_fee_assignments (id),
    CONSTRAINT fk_invoices_installment FOREIGN KEY (fee_structure_installment_id) REFERENCES fee_structure_installments (id)
) ENGINE = InnoDB;

CREATE INDEX idx_invoices_org_year_due_date ON invoices (organisation_id, academic_year_id, due_date);
CREATE INDEX idx_invoices_org_status ON invoices (organisation_id, status);

CREATE TABLE payments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    academic_year_id  BIGINT NOT NULL,
    receipt_number    VARCHAR(50) NOT NULL,
    amount            DECIMAL(10, 2) NOT NULL,
    payment_method    VARCHAR(20) NOT NULL,
    payment_date      DATE NOT NULL,
    status            VARCHAR(20) NOT NULL,
    remarks           VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_payments_org_receipt_number UNIQUE (organisation_id, receipt_number),
    CONSTRAINT fk_payments_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_payments_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id)
) ENGINE = InnoDB;

CREATE INDEX idx_payments_org_year_date ON payments (organisation_id, academic_year_id, payment_date);

CREATE TABLE payment_allocations (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    payment_id         BIGINT NOT NULL,
    invoice_id         BIGINT NOT NULL,
    allocated_amount   DECIMAL(10, 2) NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT uq_payment_allocations_payment_invoice UNIQUE (payment_id, invoice_id),
    CONSTRAINT fk_payment_allocations_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_payment_allocations_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_payment_allocations_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
) ENGINE = InnoDB;

CREATE INDEX idx_payment_allocations_invoice ON payment_allocations (invoice_id);

CREATE TABLE refunds (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    payment_id       BIGINT NOT NULL,
    invoice_id       BIGINT NOT NULL,
    amount           DECIMAL(10, 2) NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    approved_by      BIGINT NOT NULL,
    refund_date      DATE NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_refunds_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_refunds_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
) ENGINE = InnoDB;

CREATE INDEX idx_refunds_payment ON refunds (payment_id);
CREATE INDEX idx_refunds_invoice ON refunds (invoice_id);

CREATE TABLE fee_document_counters (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    academic_year_id  BIGINT NOT NULL,
    document_type     VARCHAR(20) NOT NULL,
    next_number       BIGINT NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_fee_document_counters_year_type UNIQUE (organisation_id, academic_year_id, document_type),
    CONSTRAINT fk_fee_document_counters_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_fee_document_counters_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id)
) ENGINE = InnoDB;
