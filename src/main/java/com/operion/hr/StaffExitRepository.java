package com.operion.hr;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffExitRepository extends JpaRepository<StaffExit, Long> {

	List<StaffExit> findByStaffProfileId(Long staffProfileId);
}
