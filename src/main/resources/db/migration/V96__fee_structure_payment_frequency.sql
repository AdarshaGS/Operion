ALTER TABLE fee_structures
    ADD COLUMN payment_frequency        VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN one_shot_discount_amount DECIMAL(10, 2) NULL;
