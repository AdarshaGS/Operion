package com.operion.organisation;

import com.operion.common.persistence.PlatformPlane;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface OrganisationRepository extends JpaRepository<Organisation, Long> {

	Optional<Organisation> findBySlug(String slug);
}
