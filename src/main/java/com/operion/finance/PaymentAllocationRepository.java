package com.operion.finance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

	List<PaymentAllocation> findByPaymentId(Long paymentId);

	List<PaymentAllocation> findByInvoiceId(Long invoiceId);
}
