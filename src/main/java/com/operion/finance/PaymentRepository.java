package com.operion.finance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	/** Dashboard fee-collection chart - cleared payments per day (bounced ones never count as collected). */
	@Query("SELECT p.paymentDate, SUM(p.amount) FROM Payment p WHERE p.status = :status "
			+ "AND p.paymentDate >= :fromDate AND p.paymentDate <= :toDate GROUP BY p.paymentDate")
	List<Object[]> sumAmountByPaymentDateBetween(
			@Param("status") PaymentStatus status, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

	/** Row lock so two first prints of the same receipt can't both freeze a snapshot. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.id = :id")
	Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}
