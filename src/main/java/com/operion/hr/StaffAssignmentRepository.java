package com.operion.hr;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {

	List<StaffAssignment> findByStaffProfileIdOrderByStartDateDesc(Long staffProfileId);

	Optional<StaffAssignment> findByStaffProfileIdAndStatus(Long staffProfileId, StaffAssignmentStatus status);
}
