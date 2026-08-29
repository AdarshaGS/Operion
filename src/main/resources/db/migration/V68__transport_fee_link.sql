-- StudentTransportAssignment can optionally link to a StudentFeeAssignment so a
-- transport fee flows through the existing invoice/payment/refund pipeline instead of
-- a parallel one. See ai-context/erp-system-plan.md §3.3 and issue #160.

ALTER TABLE student_transport_assignments
    ADD COLUMN student_fee_assignment_id BIGINT,
    ADD CONSTRAINT fk_transport_assignments_fee_assignment
        FOREIGN KEY (student_fee_assignment_id) REFERENCES student_fee_assignments (id);
