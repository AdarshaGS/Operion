package com.operion.hr;

import java.time.LocalDate;
import java.util.List;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

	/** Row-locked read (SELECT ... FOR UPDATE) for decisions that must not interleave, e.g. approve. A lock needs a
	 * transaction: this joins the caller's (HrService.approve holds it to commit) and only opens its own when called bare. */
	@Transactional
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<LeaveRequest> findWithLockById(Long id);

	List<LeaveRequest> findByStaffProfileIdAndStatus(Long staffProfileId, LeaveRequestStatus status);

	/** Used by StaffLeaveStatusScheduler to decide whether "today" falls inside an approved leave. */
	boolean existsByStaffProfileIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
			Long staffProfileId, LeaveRequestStatus status, LocalDate onOrAfterStart, LocalDate onOrBeforeEnd);

	/** Leave of one status overlapping a date range: starts on or before its end and ends on or after its start (leave cover lookup). */
	List<LeaveRequest> findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
			LeaveRequestStatus status, LocalDate rangeEnd, LocalDate rangeStart);

	List<LeaveRequest> findByStaffProfileId(Long staffProfileId);

	List<LeaveRequest> findByStatus(LeaveRequestStatus status);

	@Query("SELECT COALESCE(SUM(r.numberOfDays), 0) FROM LeaveRequest r "
			+ "WHERE r.staffProfile.id = :staffProfileId AND r.leaveType.id = :leaveTypeId "
			+ "AND r.academicYear.id = :academicYearId AND r.status = :status")
	double sumDaysByStaffProfileIdAndLeaveTypeIdAndAcademicYearIdAndStatus(@Param("staffProfileId") Long staffProfileId,
			@Param("leaveTypeId") Long leaveTypeId, @Param("academicYearId") Long academicYearId, @Param("status") LeaveRequestStatus status);
}
