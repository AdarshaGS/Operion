package com.operion.sales;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SalePaymentRepository extends JpaRepository<SalePayment, Long> {

	List<SalePayment> findBySaleIdOrderByPaidAtDesc(Long saleId);
}
