package com.operion.identity.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	List<RefreshToken> findByRevokedFalse();

	List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

	/** Housekeeping: removes tokens that can never be redeemed again (revoked, or past expiry). Tenant-scoped
	 * like every query here - the caller sets TenantContext to one organisation per call. */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from RefreshToken t where t.revoked = true or t.expiresAt < :cutoff")
	int deleteSpent(@Param("cutoff") Instant cutoff);
}
