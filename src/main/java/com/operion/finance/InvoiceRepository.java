package com.operion.finance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

	Optional<Invoice> findByStudentFeeAssignmentIdAndFeeStructureInstallmentId(Long studentFeeAssignmentId, Long feeStructureInstallmentId);

	List<Invoice> findByStudentFeeAssignmentId(Long studentFeeAssignmentId);

	List<Invoice> findByStudentFeeAssignment_StudentEnrollmentId(Long studentEnrollmentId);

	boolean existsByStudentFeeAssignmentId(Long studentFeeAssignmentId);
}
