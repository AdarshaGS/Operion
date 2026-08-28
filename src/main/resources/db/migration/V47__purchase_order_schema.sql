-- Purchase module schema (GitHub #56): PurchaseOrder + PurchaseOrderLine.
-- The procurement workflow ai-context/erp-system-plan.md's "don't build a
-- warehouse/procurement system unless a school actually needs it" warning was about -
-- re-confirmed with the user before starting (see load-context.md's Milestone 6 note).
-- Lines reference their parent by FK only (purchase_order_id), no owning-side collection
-- on PurchaseOrder - same convention as StockEntry/StockIssue/StockAdjustment referencing
-- Item/Campus without a collection on either side.

CREATE TABLE purchase_orders (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    supplier_id       BIGINT NOT NULL,
    campus_id         BIGINT NOT NULL,
    expected_date     DATE NOT NULL,
    status            VARCHAR(20) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_purchase_orders_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_purchase_orders_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_purchase_orders_status ON purchase_orders (organisation_id, status);

CREATE TABLE purchase_order_lines (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    purchase_order_id   BIGINT NOT NULL,
    item_id             BIGINT NOT NULL,
    quantity            INT NOT NULL,
    unit_cost           DECIMAL(10, 2) NOT NULL,
    quantity_received   INT NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_purchase_order_lines_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_purchase_order_lines_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_purchase_order_lines_item FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE = InnoDB;

CREATE INDEX idx_purchase_order_lines_order ON purchase_order_lines (organisation_id, purchase_order_id);
