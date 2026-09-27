package com.operion.library;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LibrarySettingsRepository extends JpaRepository<LibrarySettings, Long> {

	Optional<LibrarySettings> findByOrganisationId(Long organisationId);
}
