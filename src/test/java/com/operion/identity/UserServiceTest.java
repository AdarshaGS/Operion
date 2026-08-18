package com.operion.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.operion.audit.AuditLog;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Covers #44/#45 - status change (also the deactivate path) and contact update. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserServiceTest {

	private UserService userService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@BeforeEach
	void setUp() {
		userService = new UserService(userRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));

		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", "user-svc-test-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void changeStatusUpdatesAndPersistsTheStatusAndRecordsAnAuditEntry() {
		Long userId = userRepository.save(new User("status-change@user-svc.test", null, "hash")).getId();

		User locked = userService.changeStatus(userId, UserStatus.LOCKED);
		assertThat(locked.getStatus()).isEqualTo(UserStatus.LOCKED);
		assertThat(userRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.LOCKED);

		AuditLog entry = auditLogRepository.findAll().stream()
				.filter(log -> log.getEntityType().equals("User") && log.getEntityId().equals(userId))
				.findFirst()
				.orElseThrow();
		assertThat(entry.getAction()).isEqualTo("STATUS_CHANGE");
	}

	@Test
	void deactivatingIsJustSettingStatusToDisabled() {
		Long userId = userRepository.save(new User("deactivate@user-svc.test", null, "hash")).getId();

		User disabled = userService.changeStatus(userId, UserStatus.DISABLED);
		assertThat(disabled.getStatus()).isEqualTo(UserStatus.DISABLED);
	}

	@Test
	void updateChangesEmailAndPhoneAndPersistsThem() {
		Long userId = userRepository.save(new User("before@user-svc.test", "1111111111", "hash")).getId();

		User updated = userService.update(userId, "after@user-svc.test", "2222222222");
		assertThat(updated.getEmail()).isEqualTo("after@user-svc.test");
		assertThat(updated.getPhone()).isEqualTo("2222222222");

		User reloaded = userRepository.findById(userId).orElseThrow();
		assertThat(reloaded.getEmail()).isEqualTo("after@user-svc.test");
		assertThat(reloaded.getPhone()).isEqualTo("2222222222");
	}
}
