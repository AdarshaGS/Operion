package com.operion.hr;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

	Optional<LeaveBalance> findByStaffProfileIdAndLeaveTypeIdAndAcademicYearId(Long staffProfileId, Long leaveTypeId, Long academicYearId);

	List<LeaveBalance> findByStaffProfileIdAndAcademicYearId(Long staffProfileId, Long academicYearId);
}
