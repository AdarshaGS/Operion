package com.operion.platform;

import com.operion.common.persistence.PlatformPlane;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {

	Optional<PlatformAdmin> findByEmail(String email);
}
