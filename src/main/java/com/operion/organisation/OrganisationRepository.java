package com.operion.organisation;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationRepository extends JpaRepository<Organisation, Long> {

	Optional<Organisation> findBySlug(String slug);
}
