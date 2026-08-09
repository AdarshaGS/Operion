package com.operion.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAttendanceRepository extends JpaRepository<StaffAttendance, Long> {

	Optional<StaffAttendance> findByPersonIdAndAttendanceDate(Long personId, LocalDate attendanceDate);

	List<StaffAttendance> findByCampusIdAndAttendanceDate(Long campusId, LocalDate attendanceDate);
}
