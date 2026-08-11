package com.operion.platform;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {

	Optional<PlatformAdmin> findByEmail(String email);
}
