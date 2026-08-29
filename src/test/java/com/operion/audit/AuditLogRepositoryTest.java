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
		auditLogRepository.save(new AuditLog(1L, 10L, "Student", 1L, "CREATE", null, null));
		auditLogRepository.save(new AuditLog(1L, 11L, "Invoice", 2L, "CREATE", null, null));
		auditLogRepository.save(new AuditLog(2L, 10L, "Student", 3L, "CREATE", null, null));

		Instant cutoff = Instant.now();
		auditLogRepository.save(new AuditLog(1L, 10L, "Student", 4L, "UPDATE", null, null));

		// No filters beyond organisation - only org 1's three rows come back.
		assertThat(auditLogRepository.search(1L, null, null, null, null, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(3);

		// entityType narrows within the organisation.
		assertThat(auditLogRepository.search(1L, "Student", null, null, null, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(2);

		// actorUserId narrows independently of entityType.
		assertThat(auditLogRepository.search(1L, null, 11L, null, null, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(1);

		// from narrows to rows at/after the cutoff.
		assertThat(auditLogRepository.search(1L, null, null, cutoff, null, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(1);

		// Tenant boundary: org 2's row never appears in org 1's results regardless of filters.
		assertThat(auditLogRepository.search(1L, null, null, null, null, PageRequest.of(0, 50)).getContent())
				.extracting(AuditLog::getOrganisationId)
				.doesNotContain(2L);

		assertThat(auditLogRepository.findDistinctEntityTypes(1L)).containsExactlyInAnyOrder("Student", "Invoice");
	}
}
