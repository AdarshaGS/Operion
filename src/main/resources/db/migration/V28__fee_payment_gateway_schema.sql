-- "Pay this invoice" links backed by Razorpay - see
-- ai-context/load-context.md's Fees section for the Invoice/Payment/PaymentAllocation
-- convention this reuses rather than duplicating. link_token/gateway_order_id are
-- globally unique (not per-org) - both are generated with enough entropy that a
-- cross-org collision is not a practical concern, and it keeps both lookups (staff
-- generating a link, Razorpay's webhook resolving an order) simple single-column
-- lookups rather than composite keys.

CREATE TABLE payment_gateway_orders (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    invoice_id         BIGINT NOT NULL,
    payment_id         BIGINT,
    link_token         VARCHAR(64) NOT NULL,
    gateway_order_id   VARCHAR(100),
    amount             DECIMAL(10,2),
    status             VARCHAR(20) NOT NULL,
    expires_at         DATETIME(6) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT uq_payment_gateway_orders_link_token UNIQUE (link_token),
    CONSTRAINT uq_payment_gateway_orders_gateway_order_id UNIQUE (gateway_order_id),
    CONSTRAINT fk_pgo_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_pgo_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_pgo_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB;
