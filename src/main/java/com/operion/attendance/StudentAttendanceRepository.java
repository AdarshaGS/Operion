package com.operion.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentAttendanceRepository extends JpaRepository<StudentAttendance, Long> {

	Optional<StudentAttendance> findByStudentEnrollmentIdAndAttendanceDate(Long studentEnrollmentId, LocalDate attendanceDate);

	List<StudentAttendance> findBySectionIdAndAttendanceDate(Long sectionId, LocalDate attendanceDate);

	List<StudentAttendance> findByStudentEnrollmentIdAndAttendanceDateBetween(
			Long studentEnrollmentId, LocalDate fromDate, LocalDate toDate);

	long countByAttendanceDate(LocalDate attendanceDate);

	/** Feeds the dashboard attendance trend - one row per (day, status) with its count, so the
	 * caller can derive each day's present-rate without loading individual records. */
	@Query("SELECT a.attendanceDate, a.attendanceStatus, COUNT(a) FROM StudentAttendance a "
			+ "WHERE a.attendanceDate >= :fromDate AND a.attendanceDate <= :toDate "
			+ "GROUP BY a.attendanceDate, a.attendanceStatus")
	List<Object[]> countByDateAndStatusBetween(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

	long countByAttendanceDateAndAttendanceStatus(LocalDate attendanceDate, AttendanceStatus attendanceStatus);
}
