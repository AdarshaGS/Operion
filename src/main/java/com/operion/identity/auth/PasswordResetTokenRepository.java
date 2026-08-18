package com.operion.identity.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	List<PasswordResetToken> findByConsumedFalse();
}
