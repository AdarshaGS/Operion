package com.operion.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	Page<AuditLog> findByOrganisationId(Long organisationId, Pageable pageable);

	/** Cross-org, for the platform-admin activity feed - AuditLog carries no @TenantId (see
	 * its own javadoc), so a plain findAll() would return every organisation's rows, same
	 * "the platform plane is the one place cross-tenant listing is allowed" precedent as
	 * BillingService.allSubscriptions()/allInvoices(). But this table is shared by every
	 * module (attendance, reporting, students, ...), not just the platform-billing ones -
	 * without the entityType filter this drags in tenant-level noise (e.g. a school's own
	 * "SavedReport run" entries) that has nothing to do with the platform-admin feed.
	 * entityType is deliberately a literal Java list, not a DB enum: AuditLog's own javadoc
	 * says entity_type/entity_id is intentionally schema-free, so there's nothing to join
	 * against - this list is just "what BillingService/OrganisationService currently log". */
	List<AuditLog> findTop50ByEntityTypeInOrderByOccurredAtDesc(List<String> entityTypes);

	/** All filters optional - the frontend viewer (#145) narrows by any combination of
	 * entity type / actor / date range, or none at all. */
	@Query("SELECT a FROM AuditLog a WHERE a.organisationId = :organisationId "
			+ "AND (:entityType IS NULL OR a.entityType = :entityType) "
			+ "AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId) "
			+ "AND (:from IS NULL OR a.occurredAt >= :from) "
			+ "AND (:to IS NULL OR a.occurredAt <= :to)")
	Page<AuditLog> search(@Param("organisationId") Long organisationId, @Param("entityType") String entityType,
			@Param("actorUserId") Long actorUserId, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

	@Query("SELECT DISTINCT a.entityType FROM AuditLog a WHERE a.organisationId = :organisationId ORDER BY a.entityType")
	List<String> findDistinctEntityTypes(@Param("organisationId") Long organisationId);
}
