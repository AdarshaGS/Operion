package com.operion.examination;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExaminationSettingsRepository extends JpaRepository<ExaminationSettings, Long> {

	Optional<ExaminationSettings> findByOrganisationId(Long organisationId);
}
