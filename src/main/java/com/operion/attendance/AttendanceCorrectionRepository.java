package com.operion.attendance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {

	List<AttendanceCorrection> findByStudentAttendanceId(Long studentAttendanceId);
}
