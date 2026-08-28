package com.operion.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

	List<Sale> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

	@Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.saleDate = :date")
	BigDecimal sumTotalAmountBySaleDate(@Param("date") LocalDate date);

	@Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.saleDate >= :from")
	BigDecimal sumTotalAmountBySaleDateGreaterThanEqual(@Param("from") LocalDate from);
}
