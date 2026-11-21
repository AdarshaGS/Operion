package com.operion.email;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

	/** A page of ids for RetentionScheduler's purge; tenant-filtered like every query on a TenantScopedEntity. */
	@Query("select e.id from EmailOutbox e where e.status in :statuses and e.createdAt < :cutoff")
	List<Long> findIdsByStatusInAndCreatedAtBefore(@Param("statuses") Collection<EmailDeliveryStatus> statuses,
			@Param("cutoff") Instant cutoff, Pageable pageable);
}
