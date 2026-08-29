package com.operion.student;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface StudentIdCounterRepository extends JpaRepository<StudentIdCounter, Long> {

	/** Locked so two concurrent admissions in the same (org, year) never hand out the same student ID. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<StudentIdCounter> findByYear(int year);
}
