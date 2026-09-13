package com.operion.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/** The demand view (#237) - every not-fully-paid invoice past its due date, optionally
	 * narrowed to one class or one student enrollment. "Not fully paid" is amountPaid <
	 * totalAmount rather than a status check, so it stays correct regardless of how the
	 * outstanding balance moved (payment, bounce, refund, adjustment, or waiver). */
	@Query("SELECT i FROM Invoice i WHERE i.amountPaid < i.totalAmount AND i.dueDate < :asOf "
			+ "AND (:schoolClassId IS NULL OR i.studentFeeAssignment.studentEnrollment.section.schoolClass.id = :schoolClassId) "
			+ "AND (:studentEnrollmentId IS NULL OR i.studentFeeAssignment.studentEnrollment.id = :studentEnrollmentId) "
			+ "ORDER BY i.dueDate ASC")
	List<Invoice> findOverdue(
			@Param("asOf") LocalDate asOf, @Param("schoolClassId") Long schoolClassId, @Param("studentEnrollmentId") Long studentEnrollmentId);
}
