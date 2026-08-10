package com.operion.finance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentFeeAssignmentRepository extends JpaRepository<StudentFeeAssignment, Long> {

	List<StudentFeeAssignment> findByStudentEnrollmentId(Long studentEnrollmentId);

	List<StudentFeeAssignment> findByStudentEnrollmentIdAndStatus(Long studentEnrollmentId, StudentFeeAssignmentStatus status);
}
