-- #173: item setup wants storage location, a structured supplier link, and issue cost.
-- Nullable throughout - existing items/entries/issues are unaffected. source on
-- stock_entries stays free text (kept as a fallback for ad-hoc sources per the
-- decision on #173); supplier_id is an additional optional structured link alongside it.

ALTER TABLE items
    ADD COLUMN storage_location VARCHAR(255),
    ADD COLUMN default_supplier_id BIGINT,
    ADD CONSTRAINT fk_items_default_supplier FOREIGN KEY (default_supplier_id) REFERENCES suppliers (id);

ALTER TABLE stock_entries
    ADD COLUMN supplier_id BIGINT,
    ADD CONSTRAINT fk_stock_entries_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id);

ALTER TABLE stock_issues
    ADD COLUMN unit_cost DECIMAL(10, 2);
