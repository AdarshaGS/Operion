package com.operion.dashboard;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardPreferenceRepository extends JpaRepository<DashboardPreference, Long> {

	Optional<DashboardPreference> findByUserId(Long userId);
}
