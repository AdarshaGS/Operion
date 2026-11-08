package com.operion.student;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionSettingsRepository extends JpaRepository<AdmissionSettings, Long> {

	Optional<AdmissionSettings> findByOrganisationId(Long organisationId);
}
