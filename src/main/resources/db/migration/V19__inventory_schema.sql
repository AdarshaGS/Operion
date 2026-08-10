-- Inventory module schema: ItemCategory, Item, StockEntry, StockIssue, StockAdjustment.
-- See ai-context/erp-system-plan.md §3.3 for the light sketch this deep-designed.
-- Deliberately not a warehouse/procurement system - no vendor POs, no multi-campus
-- transfers. Balance is computed live from the three ledger tables (per the design
-- sign-off), not stored - no ItemStockBalance projection table exists.

CREATE TABLE item_categories (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    code              VARCHAR(30) NOT NULL,
    name              VARCHAR(100) NOT NULL,
    description       VARCHAR(500),
    status            VARCHAR(20) NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_item_categories_org_code UNIQUE (organisation_id, code),
    CONSTRAINT fk_item_categories_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE items (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    item_category_id    BIGINT NOT NULL,
    code                VARCHAR(30) NOT NULL,
    name                VARCHAR(150) NOT NULL,
    unit                VARCHAR(20) NOT NULL,
    description         VARCHAR(500),
    status              VARCHAR(20) NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_items_org_code UNIQUE (organisation_id, code),
    CONSTRAINT fk_items_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_items_category FOREIGN KEY (item_category_id) REFERENCES item_categories (id)
) ENGINE = InnoDB;

CREATE INDEX idx_items_category ON items (organisation_id, item_category_id);

CREATE TABLE stock_entries (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    item_id           BIGINT NOT NULL,
    campus_id         BIGINT NOT NULL,
    quantity          INT NOT NULL,
    unit_cost         DECIMAL(10, 2),
    entry_date        DATE NOT NULL,
    source            VARCHAR(200),
    remarks           VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_stock_entries_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_stock_entries_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_stock_entries_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_stock_entries_item_campus ON stock_entries (organisation_id, item_id, campus_id);

CREATE TABLE stock_issues (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    item_id           BIGINT NOT NULL,
    campus_id         BIGINT NOT NULL,
    quantity          INT NOT NULL,
    issued_date       DATE NOT NULL,
    issued_to         VARCHAR(200) NOT NULL,
    purpose           VARCHAR(500),
    remarks           VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_stock_issues_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_stock_issues_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_stock_issues_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_stock_issues_item_campus ON stock_issues (organisation_id, item_id, campus_id);

CREATE TABLE stock_adjustments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    item_id           BIGINT NOT NULL,
    campus_id         BIGINT NOT NULL,
    quantity_delta    INT NOT NULL,
    reason            VARCHAR(20) NOT NULL,
    adjustment_date   DATE NOT NULL,
    remarks           VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_stock_adjustments_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_stock_adjustments_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_stock_adjustments_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_stock_adjustments_item_campus ON stock_adjustments (organisation_id, item_id, campus_id);
