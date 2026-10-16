package com.operion.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

	/** Row lock for recording a payment, so concurrent payments cannot both pass the outstanding-amount check. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Sale s WHERE s.id = :id")
	Optional<Sale> findByIdForUpdate(@Param("id") Long id);

	List<Sale> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

	@Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.saleDate = :date")
	BigDecimal sumTotalAmountBySaleDate(@Param("date") LocalDate date);

	@Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.saleDate >= :from")
	BigDecimal sumTotalAmountBySaleDateGreaterThanEqual(@Param("from") LocalDate from);
}
