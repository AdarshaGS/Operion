package com.operion.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

	Optional<Invoice> findByStudentFeeAssignmentIdAndFeeStructureInstallmentId(Long studentFeeAssignmentId, Long feeStructureInstallmentId);

	List<Invoice> findByStudentFeeAssignmentId(Long studentFeeAssignmentId);

	List<Invoice> findByStudentFeeAssignment_StudentEnrollmentId(Long studentEnrollmentId);

	boolean existsByStudentFeeAssignmentId(Long studentFeeAssignmentId);

	@Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i")
	BigDecimal sumTotalAmount();

	@Query("SELECT COALESCE(SUM(i.amountPaid), 0) FROM Invoice i")
	BigDecimal sumAmountPaid();

	long countByStatusNotAndDueDateBefore(InvoiceStatus status, LocalDate dueDate);
}
