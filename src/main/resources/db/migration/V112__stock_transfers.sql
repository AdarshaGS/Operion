-- #175: stock transfer between campuses, recorded as its own insert-only audit trail.
-- The actual balance effect is two StockAdjustment rows (TRANSFER_OUT at the source,
-- TRANSFER_IN at the destination) written alongside this, per the same pattern
-- PurchaseReturn already established - keeps getBalance/getLowStockItems from needing
-- to know about a fourth balance-affecting ledger table.

CREATE TABLE stock_transfers (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    item_id           BIGINT NOT NULL,
    from_campus_id    BIGINT NOT NULL,
    to_campus_id      BIGINT NOT NULL,
    quantity          INT NOT NULL,
    transfer_date     DATE NOT NULL,
    remarks           VARCHAR(500),
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_stock_transfers_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_stock_transfers_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_stock_transfers_from_campus FOREIGN KEY (from_campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_stock_transfers_to_campus FOREIGN KEY (to_campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_stock_transfers_item ON stock_transfers (organisation_id, item_id);

