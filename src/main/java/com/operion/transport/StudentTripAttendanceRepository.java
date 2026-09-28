package com.operion.transport;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentTripAttendanceRepository extends JpaRepository<StudentTripAttendance, Long> {

	Optional<StudentTripAttendance> findByTripLogIdAndStudentEnrollmentId(Long tripLogId, Long studentEnrollmentId);

	List<StudentTripAttendance> findByTripLogId(Long tripLogId);
}
