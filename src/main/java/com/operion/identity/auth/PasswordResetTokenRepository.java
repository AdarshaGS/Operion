package com.operion.identity.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	/** Indexed lookup by the token's SHA-256 hash (idx_password_reset_tokens_org_hash); tenant-scoped like every query here. */
	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/**
	 * The single-use gate: flips consumed in one conditional UPDATE and returns the rows changed, so of
	 * two concurrent confirms with the same token exactly one sees 1. Callers must treat anything but 1 as invalid.
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update PasswordResetToken t set t.consumed = true where t.id = :id and t.consumed = false and t.expiresAt > :now")
	int consumeIfValid(@Param("id") Long id, @Param("now") Instant now);

	/** Invalidates every still-unused token for the user (a newer request, or a completed reset, supersedes them). */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update PasswordResetToken t set t.consumed = true where t.userId = :userId and t.consumed = false")
	int consumeAllForUser(@Param("userId") Long userId);
}
