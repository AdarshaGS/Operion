-- Supplier master data (GitHub #50) - a vendor address book, not a procurement workflow.
-- See com.operion.inventory.Supplier's class doc for the scope call this was made
-- against.
CREATE TABLE suppliers (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    name              VARCHAR(200) NOT NULL,
    contact_person    VARCHAR(200),
    phone             VARCHAR(30),
    email             VARCHAR(255),
    address           VARCHAR(500),
    status            VARCHAR(20) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_suppliers_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_suppliers_org_status ON suppliers (organisation_id, status);
