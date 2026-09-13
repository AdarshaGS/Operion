package com.operion.finance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdjustmentRepository extends JpaRepository<Adjustment, Long> {

	List<Adjustment> findByInvoiceId(Long invoiceId);
}
