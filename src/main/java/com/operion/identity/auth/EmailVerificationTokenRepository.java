package com.operion.identity.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

	List<EmailVerificationToken> findByConsumedFalse();
}
