package com.operion.dashboard;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface DashboardPreferenceRepository extends JpaRepository<DashboardPreference, Long> {

	Optional<DashboardPreference> findByUserId(Long userId);

	/** Own transaction so a concurrent first dismissal that wins the insert race (unique org+user)
	 * only costs the loser this insert, never the dismissal itself - callers ignore the conflict. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	default void createIfMissing(Long userId) {
		if (findByUserId(userId).isEmpty()) {
			saveAndFlush(new DashboardPreference(userId));
		}
	}

	// Single-column updates: two different dismissals running at once must not overwrite each
	// other's flag, which a load-modify-save of the whole row would.
	@Transactional
	@Modifying
	@Query("UPDATE DashboardPreference p SET p.setupProgressDismissed = true WHERE p.userId = :userId")
	int markSetupProgressDismissed(@Param("userId") Long userId);

	@Transactional
	@Modifying
	@Query("UPDATE DashboardPreference p SET p.quickActionsDismissed = true WHERE p.userId = :userId")
	int markQuickActionsDismissed(@Param("userId") Long userId);
}
