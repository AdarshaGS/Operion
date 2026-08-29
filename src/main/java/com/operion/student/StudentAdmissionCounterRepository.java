package com.operion.student;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface StudentAdmissionCounterRepository extends JpaRepository<StudentAdmissionCounter, Long> {

	/** Locked so two concurrent admissions in the same calendar year never hand out the same number. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<StudentAdmissionCounter> findByCalendarYear(int calendarYear);
}
