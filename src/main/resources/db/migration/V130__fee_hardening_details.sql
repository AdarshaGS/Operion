-- Cheque bank name captured alongside the cheque number (payment_reference), per #282.
ALTER TABLE payments
    ADD COLUMN payment_bank_name VARCHAR(100) NULL;

-- Payer-chosen frequency (null = the structure's own label) and optional approval proof for
-- the discount on an assignment, per #281 / #280.
ALTER TABLE student_fee_assignments
    ADD COLUMN payment_frequency    VARCHAR(20)  NULL,
    ADD COLUMN proof_file_reference VARCHAR(255) NULL,
    ADD COLUMN proof_file_name      VARCHAR(255) NULL;
