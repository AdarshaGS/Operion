package com.operion.certificate;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CertificateRequestRepository extends JpaRepository<CertificateRequest, Long> {

	List<CertificateRequest> findByStudentIdOrderByIdDesc(Long studentId);

	List<CertificateRequest> findAllByOrderByIdDesc();

	List<CertificateRequest> findByStatusOrderByIdDesc(CertificateRequestStatus status);

	boolean existsByStudentIdAndCertificateTypeIdAndStatusIn(Long studentId, Long certificateTypeId, Collection<CertificateRequestStatus> statuses);

	Optional<CertificateRequest> findByVerificationToken(String verificationToken);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from CertificateRequest r where r.id = :id")
	Optional<CertificateRequest> findByIdForUpdate(@Param("id") Long id);

	// Clearance and merge-field lookups across modules, read-only. Kept here rather than added to each
	// module's repository so certificates stay a pure consumer of those modules' tables.

	@Query("select coalesce(sum(i.totalAmount - i.amountPaid), 0) from Invoice i"
			+ " where i.studentFeeAssignment.studentEnrollment.student.id = :studentId and i.status <> com.operion.finance.InvoiceStatus.PAID")
	BigDecimal outstandingFees(@Param("studentId") Long studentId);

	@Query("select count(b) from BorrowRecord b where b.borrower.id = :personId and b.status = com.operion.library.BorrowStatus.BORROWED")
	long openLibraryLoans(@Param("personId") Long personId);

	@Query("select coalesce(sum(f.amount), 0) from Fine f"
			+ " where f.borrowRecord.borrower.id = :personId and f.status = com.operion.library.FineStatus.PENDING")
	BigDecimal pendingLibraryFines(@Param("personId") Long personId);

	@Query("select count(t) from StudentTransportAssignment t"
			+ " where t.studentEnrollment.student.id = :studentId and t.status = com.operion.transport.TransportAssignmentStatus.ACTIVE")
	long activeTransportAssignments(@Param("studentId") Long studentId);

	/** [AttendanceStatus, count] for one enrollment. */
	@Query("select a.attendanceStatus, count(a) from StudentAttendance a where a.studentEnrollment.id = :enrollmentId group by a.attendanceStatus")
	List<Object[]> attendanceCounts(@Param("enrollmentId") Long enrollmentId);
}
