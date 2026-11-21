package com.operion.identity.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

	/** Keys of one organisation's counters untouched since {@code cutoff} and not locked as of {@code now} - RetentionScheduler's purge page. */
	@Query("select a.key from LoginAttempt a where a.organisationId = :organisationId and a.updatedAt < :cutoff "
			+ "and (a.lockedUntil is null or a.lockedUntil <= :now)")
	List<String> findPurgeableKeys(@Param("organisationId") Long organisationId, @Param("cutoff") Instant cutoff,
			@Param("now") Instant now, Pageable pageable);
}
