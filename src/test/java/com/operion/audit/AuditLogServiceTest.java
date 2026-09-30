package com.operion.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.RequestOrigin;
import com.operion.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** #193 (IP/user-agent capture) and #194 (sensitive-value redaction) on AuditLogService -
 * hand-constructed, no ObjectMapper bean in the DataJpaTest slice. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditLogServiceTest {

	@Autowired
	private AuditLogRepository auditLogRepository;

	@AfterEach
	void clear() {
		RequestOrigin.clear();
		TenantContext.clear();
	}

	private AuditLogService service() {
		return new AuditLogService(auditLogRepository, new ObjectMapper());
	}

	@Test
	void recordsRequestIpAndUserAgent() {
		RequestOrigin.set("203.0.113.9", "JUnit/1.0");
		service().record("Widget", -501L, "CREATE", null, "x");

		AuditLog row = auditLogRepository.findAll().stream().filter(a -> a.getEntityId() == -501L).findFirst().orElseThrow();
		assertThat(row.getIpAddress()).isEqualTo("203.0.113.9");
		assertThat(row.getUserAgent()).isEqualTo("JUnit/1.0");
	}

	@Test
	void originIsNullOutsideARequest() {
		service().record("Widget", -502L, "CREATE", null, "x");

		AuditLog row = auditLogRepository.findAll().stream().filter(a -> a.getEntityId() == -502L).findFirst().orElseThrow();
		assertThat(row.getIpAddress()).isNull();
		assertThat(row.getUserAgent()).isNull();
	}

	@Test
	void redactsSensitivePropertiesAtAnyDepthButKeepsTheRest() {
		Map<String, Object> after = Map.of("status", "ACTIVE", "passwordHash", "$2a$secret", "profile",
				Map.of("bankAccountNumber", "1234567890", "city", "Pune"), "items", List.of(Map.of("refresh_token", "abc", "n", 1)));
		service().record("Widget", -503L, "UPDATE", null, after);

		String json = auditLogRepository.findAll().stream().filter(a -> a.getEntityId() == -503L).findFirst().orElseThrow().getAfterValue();
		assertThat(json).doesNotContain("$2a$secret").doesNotContain("1234567890").doesNotContain("abc");
		assertThat(json).contains("ACTIVE").contains("Pune").contains(AuditLogService.REDACTED);
	}

	@Test
	void sensitiveNameMatchingIgnoresCaseAndSeparators() {
		assertThat(AuditLogService.isSensitive("Password")).isTrue();
		assertThat(AuditLogService.isSensitive("bank_account_number")).isTrue();
		assertThat(AuditLogService.isSensitive("accessToken")).isTrue();
		assertThat(AuditLogService.isSensitive("status")).isFalse();
		assertThat(AuditLogService.isSensitive("span")).isFalse();
	}
}
