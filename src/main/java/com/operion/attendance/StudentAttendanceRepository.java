package com.operion.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAttendanceRepository extends JpaRepository<StudentAttendance, Long> {

	Optional<StudentAttendance> findByStudentEnrollmentIdAndAttendanceDate(Long studentEnrollmentId, LocalDate attendanceDate);

	List<StudentAttendance> findBySectionIdAndAttendanceDate(Long sectionId, LocalDate attendanceDate);

	List<StudentAttendance> findByStudentEnrollmentIdAndAttendanceDateBetween(
			Long studentEnrollmentId, LocalDate fromDate, LocalDate toDate);
}
