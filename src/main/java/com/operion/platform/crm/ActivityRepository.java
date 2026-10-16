package com.operion.platform.crm;

import com.operion.common.persistence.PlatformPlane;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface ActivityRepository extends JpaRepository<Activity, Long> {

	List<Activity> findByLeadIdOrderByOccurredAtDesc(Long leadId);

	List<Activity> findByOrganisationIdOrderByOccurredAtDesc(Long organisationId);
}
