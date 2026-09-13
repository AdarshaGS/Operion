package com.operion.finance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WaiverRepository extends JpaRepository<Waiver, Long> {

	List<Waiver> findByInvoiceId(Long invoiceId);
}
