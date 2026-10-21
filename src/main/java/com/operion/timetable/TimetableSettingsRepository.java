package com.operion.timetable;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableSettingsRepository extends JpaRepository<TimetableSettings, Long> {

	Optional<TimetableSettings> findByOrganisationId(Long organisationId);
}
