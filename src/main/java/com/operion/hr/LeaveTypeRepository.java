package com.operion.hr;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

	List<LeaveType> findByStatus(LeaveTypeStatus status);
}
