package com.operion.finance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

	List<PaymentAllocation> findByPaymentId(Long paymentId);

	List<PaymentAllocation> findByInvoiceId(Long invoiceId);

	/** Payment carries no student/enrollment FK of its own (a single payment can cover
	 * several invoices) - this is the join back to a student's payment history (#133). */
	List<PaymentAllocation> findByInvoice_StudentFeeAssignment_StudentEnrollmentId(Long studentEnrollmentId);
}
