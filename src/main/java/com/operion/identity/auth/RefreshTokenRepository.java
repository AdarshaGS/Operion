package com.operion.identity.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	List<RefreshToken> findByRevokedFalse();

	List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);
}
