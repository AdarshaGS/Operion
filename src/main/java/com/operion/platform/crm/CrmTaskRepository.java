package com.operion.platform.crm;

import com.operion.common.persistence.PlatformPlane;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface CrmTaskRepository extends JpaRepository<CrmTask, Long> {

	List<CrmTask> findByLeadIdOrderByDueAtAsc(Long leadId);

	List<CrmTask> findByOrganisationIdOrderByDueAtAsc(Long organisationId);

	List<CrmTask> findAllByOrderByDueAtAsc();

	long countByCompletedAtIsNull();

	long countByCompletedAtIsNullAndDueAtBefore(Instant cutoff);
}
