package com.operion.attendance;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassAttendanceRegisterRepository extends JpaRepository<ClassAttendanceRegister, Long> {

	Optional<ClassAttendanceRegister> findBySectionIdAndAttendanceDate(Long sectionId, LocalDate attendanceDate);
}
