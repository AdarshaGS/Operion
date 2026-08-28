-- Sales module schema (GitHub #60-63): Sale, SaleLine, SalePayment, and the
-- per-organisation receipt-number counter. Lines/payments reference their parent by FK
-- only, no owning-side collection - same convention as PurchaseOrderLine/PurchaseOrder.

CREATE TABLE sale_receipt_counters (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    next_number       BIGINT NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_sale_receipt_counters_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE sales (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    customer_id       BIGINT NOT NULL,
    campus_id         BIGINT NOT NULL,
    receipt_number    VARCHAR(50) NOT NULL,
    sale_date         DATE NOT NULL,
    total_amount      DECIMAL(10, 2) NOT NULL,
    amount_paid       DECIMAL(10, 2) NOT NULL DEFAULT 0,
    status            VARCHAR(20) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_sales_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_sales_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_sales_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_sales_customer ON sales (organisation_id, customer_id);
CREATE INDEX idx_sales_status ON sales (organisation_id, status);

CREATE TABLE sale_lines (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    sale_id           BIGINT NOT NULL,
    item_id           BIGINT NOT NULL,
    quantity          INT NOT NULL,
    unit_price        DECIMAL(10, 2) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_sale_lines_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_sale_lines_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_sale_lines_item FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE = InnoDB;

CREATE INDEX idx_sale_lines_sale ON sale_lines (organisation_id, sale_id);

CREATE TABLE sale_payments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    sale_id           BIGINT NOT NULL,
    payment_method    VARCHAR(20) NOT NULL,
    amount            DECIMAL(10, 2) NOT NULL,
    paid_at           DATE NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_sale_payments_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_sale_payments_sale FOREIGN KEY (sale_id) REFERENCES sales (id)
) ENGINE = InnoDB;

CREATE INDEX idx_sale_payments_sale ON sale_payments (organisation_id, sale_id);
