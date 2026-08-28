-- PurchaseReturn (GitHub #58) - the audit trail linking a return back to its Purchase
-- Order line (and transitively supplier/PO), distinct from the generic StockAdjustment
-- ledger that actually moves the item's balance (see PurchaseOrderService.recordReturn).
CREATE TABLE purchase_returns (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id         BIGINT NOT NULL,
    purchase_order_line_id  BIGINT NOT NULL,
    quantity                INT NOT NULL,
    reason                  VARCHAR(20) NOT NULL,
    return_date             DATE NOT NULL,
    remarks                 VARCHAR(500),
    created_at              DATETIME(6) NOT NULL,
    updated_at              DATETIME(6) NOT NULL,
    created_by              BIGINT,
    updated_by              BIGINT,
    CONSTRAINT fk_purchase_returns_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_purchase_returns_line FOREIGN KEY (purchase_order_line_id) REFERENCES purchase_order_lines (id)
) ENGINE = InnoDB;

CREATE INDEX idx_purchase_returns_line ON purchase_returns (organisation_id, purchase_order_line_id);
