package com.operion.student;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface StudentIdCounterRepository extends JpaRepository<StudentIdCounter, Long> {

	/** Locked so two concurrent admissions in the same (org, year) never hand out the same student ID. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<StudentIdCounter> findByYear(int year);

	boolean existsByYear(int year);

	/**
	 * Creates the year's row in a transaction of its own, before the caller takes the locking read. A locking read of a
	 * row that doesn't exist yet takes a gap lock, and two admissions that each then insert the row deadlock on it - so the
	 * insert must happen first, outside the caller's transaction. A lost race (duplicate key or deadlock) throws and rolls
	 * back only this inner transaction: callers ignore it, since the winner's row is then there to lock.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	default void createIfMissing(int year) {
		if (!existsByYear(year)) {
			saveAndFlush(new StudentIdCounter(year));
		}
	}
}
