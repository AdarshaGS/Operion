package com.operion.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassAttendanceRegisterRepository extends JpaRepository<ClassAttendanceRegister, Long> {

	Optional<ClassAttendanceRegister> findBySectionIdAndAttendanceDate(Long sectionId, LocalDate attendanceDate);

	List<ClassAttendanceRegister> findByAttendanceDateBetween(LocalDate from, LocalDate to);

	/** Attendance overview - (section id, register status) for every register opened on a day. */
	@Query("SELECT r.section.id, r.registerStatus FROM ClassAttendanceRegister r WHERE r.attendanceDate = :date")
	List<Object[]> findSectionStatusesOn(@Param("date") LocalDate date);
}
