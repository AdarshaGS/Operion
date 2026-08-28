package com.operion.sales;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

	List<SaleLine> findBySaleId(Long saleId);
}
