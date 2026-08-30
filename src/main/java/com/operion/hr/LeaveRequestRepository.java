package com.operion.hr;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

	List<LeaveRequest> findByStaffProfileIdAndStatus(Long staffProfileId, LeaveRequestStatus status);

	/** Used by StaffLeaveStatusScheduler to decide whether "today" falls inside an approved leave. */
	boolean existsByStaffProfileIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
			Long staffProfileId, LeaveRequestStatus status, LocalDate onOrAfterStart, LocalDate onOrBeforeEnd);

	List<LeaveRequest> findByStaffProfileId(Long staffProfileId);

	List<LeaveRequest> findByStatus(LeaveRequestStatus status);

	@Query("SELECT COALESCE(SUM(r.numberOfDays), 0) FROM LeaveRequest r "
			+ "WHERE r.staffProfile.id = :staffProfileId AND r.leaveType.id = :leaveTypeId "
			+ "AND r.academicYear.id = :academicYearId AND r.status = :status")
	double sumDaysByStaffProfileIdAndLeaveTypeIdAndAcademicYearIdAndStatus(@Param("staffProfileId") Long staffProfileId,
			@Param("leaveTypeId") Long leaveTypeId, @Param("academicYearId") Long academicYearId, @Param("status") LeaveRequestStatus status);
}
