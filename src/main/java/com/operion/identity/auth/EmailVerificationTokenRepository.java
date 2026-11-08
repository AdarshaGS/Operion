package com.operion.identity.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

	/** Indexed lookup by the token's SHA-256 hash (idx_email_verification_tokens_org_hash); tenant-scoped. */
	Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

	/** The single-use gate - see PasswordResetTokenRepository.consumeIfValid. */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update EmailVerificationToken t set t.consumed = true where t.id = :id and t.consumed = false and t.expiresAt > :now")
	int consumeIfValid(@Param("id") Long id, @Param("now") Instant now);
}
