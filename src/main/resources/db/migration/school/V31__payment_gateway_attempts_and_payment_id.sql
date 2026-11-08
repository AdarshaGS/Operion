-- Razorpay webhook hardening.
--
-- 1. payment_gateway_attempts: every Razorpay order created for a payment link. A link
--    (payment_gateway_orders row) keeps only its latest gateway_order_id, so a parent who paid against an
--    earlier Razorpay order (two tabs, or a re-quote after the outstanding changed) was captured by Razorpay but
--    unresolvable by the webhook. The webhook now resolves the organisation and the quoted amount from this
--    table, so every earlier order id stays resolvable. organisation_id has no FK: organisations live in the
--    Platform database.
-- 2. payment_gateway_orders.gateway_payment_id: the Razorpay payment id that settled the link - the unique
--    constraint is the last-resort idempotency guard if the same payment is ever delivered twice concurrently
--    (NULL until paid; several NULLs are allowed under a UNIQUE constraint).

CREATE TABLE payment_gateway_attempts (
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id            BIGINT NOT NULL,
    payment_gateway_order_id   BIGINT NOT NULL,
    gateway_order_id           VARCHAR(100) NOT NULL,
    amount                     DECIMAL(10, 2) NOT NULL,
    created_at                 DATETIME(6) NOT NULL,
    updated_at                 DATETIME(6) NOT NULL,
    created_by                 BIGINT,
    updated_by                 BIGINT,
    CONSTRAINT uq_payment_gateway_attempts_gateway_order_id UNIQUE (gateway_order_id),
    CONSTRAINT fk_pga_payment_gateway_order FOREIGN KEY (payment_gateway_order_id) REFERENCES payment_gateway_orders (id)
) ENGINE = InnoDB;

-- Links that already have a gateway order keep resolving: one attempt per existing order.
INSERT INTO payment_gateway_attempts (organisation_id, payment_gateway_order_id, gateway_order_id, amount, created_at, updated_at)
SELECT organisation_id, id, gateway_order_id, amount, created_at, updated_at
FROM payment_gateway_orders
WHERE gateway_order_id IS NOT NULL AND amount IS NOT NULL;

ALTER TABLE payment_gateway_orders ADD COLUMN gateway_payment_id VARCHAR(100) NULL;
ALTER TABLE payment_gateway_orders ADD CONSTRAINT uq_payment_gateway_orders_gateway_payment_id UNIQUE (gateway_payment_id);
