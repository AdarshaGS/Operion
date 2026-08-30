package com.operion.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves search()'s filters (#145) are each independently optional (":param IS NULL OR
 * ...") and that AuditLog's explicit organisationId (not @TenantId - see its own
 * javadoc) is still honoured as the tenant boundary on this read path.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditLogRepositoryTest {

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Test
	void searchFiltersByOrganisationEntityTypeActorAndDateRangeIndependently() {
		// Negative, never-colliding sentinel org ids - real Organisation rows always get a
		// positive auto-increment id, and @DataJpaTest's cached-context H2 instance is shared
		// (not reset) across every test class with this exact @Import signature, so a literal
		// positive id here would be polluted by whatever other tests happen to run first.
		long orgA = -101L;
		long orgB = -102L;
		auditLogRepository.save(new AuditLog(orgA, 10L, "Student", 1L, "CREATE", null, null));
		auditLogRepository.save(new AuditLog(orgA, 11L, "Invoice", 2L, "CREATE", null, null));
		auditLogRepository.save(new AuditLog(orgB, 10L, "Student", 3L, "CREATE", null, null));

		Instant cutoff = Instant.now();
		auditLogRepository.save(new AuditLog(orgA, 10L, "Student", 4L, "UPDATE", null, null));

		// No filters beyond organisation - only org A's three rows come back.
		assertThat(auditLogRepository.search(orgA, null, null, null, null, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(3);

		// entityType narrows within the organisation.
		assertThat(auditLogRepository.search(orgA, "Student", null, null, null, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(2);

		// actorUserId narrows independently of entityType.
		assertThat(auditLogRepository.search(orgA, null, 11L, null, null, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(1);

		// from narrows to rows at/after the cutoff.
		assertThat(auditLogRepository.search(orgA, null, null, cutoff, null, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(1);

		// Tenant boundary: org B's row never appears in org A's results regardless of filters.
		assertThat(auditLogRepository.search(orgA, null, null, null, null, PageRequest.of(0, 50)).getContent())
				.extracting(AuditLog::getOrganisationId)
				.doesNotContain(orgB);

		assertThat(auditLogRepository.findDistinctEntityTypes(orgA)).containsExactlyInAnyOrder("Student", "Invoice");
	}
}
