package com.operion.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Permission;
import com.operion.authorization.PermissionRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
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
	private OrganisationMembershipRepository membershipRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PermissionRepository permissionRepository;

	@BeforeEach
	void setUp() {
		userService = new UserService(userRepository, new AuditLogService(auditLogRepository, new ObjectMapper()), membershipRepository);

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

	/** A user with an ACTIVE membership whose role carries {@code codes}; {@code owner} marks the org's Owner membership. */
	private Long member(String email, boolean owner, String... codes) {
		Role role = new Role("Role " + System.nanoTime(), "test", false);
		for (String code : codes) {
			role.grant(permissionRepository.findAll().stream().filter(p -> p.getCode().equals(code)).findFirst()
					.orElseGet(() -> permissionRepository.save(new Permission(code, "test", null))));
		}
		role = roleRepository.save(role);
		User user = userRepository.save(new User(email, null, "hash"));
		Person person = personRepository.save(new Person("Test", "Member"));
		membershipRepository.save(new OrganisationMembership(user, person, role, null, null, owner));
		return user.getId();
	}

	// SEC-M1: a login email is where a password reset goes, so changing it is an account takeover path.
	@Test
	void aMemberManagerCannotChangeTheOwnersEmail() {
		Long ownerId = member("owner@user-svc.test", true);
		Long managerId = member("manager@user-svc.test", false, "MEMBERSHIP_MANAGE", "ALL_FUNCTIONS");

		assertThatThrownBy(() -> userService.update(ownerId, "attacker@evil.test", null, managerId))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThat(userRepository.findById(ownerId).orElseThrow().getEmail()).isEqualTo("owner@user-svc.test");
	}

	@Test
	void aMemberManagerCannotChangeTheEmailOfSomeoneWithMoreAccess() {
		Long adminId = member("admin@user-svc.test", false, "US_FEES", "US_PAYROLL");
		Long managerId = member("manager2@user-svc.test", false, "MEMBERSHIP_MANAGE", "US_FEES");

		assertThatThrownBy(() -> userService.update(adminId, "attacker@evil.test", null, managerId))
				.isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void aMemberManagerCanChangeTheEmailOfSomeoneWithLessAccess() {
		Long clerkId = member("clerk@user-svc.test", false, "US_FEES");
		Long managerId = member("manager3@user-svc.test", false, "MEMBERSHIP_MANAGE", "US_FEES");

		userService.update(clerkId, "clerk.new@user-svc.test", null, managerId);

		assertThat(userRepository.findById(clerkId).orElseThrow().getEmail()).isEqualTo("clerk.new@user-svc.test");
	}

	@Test
	void theOwnerCanChangeAnyonesEmail() {
		Long ownerId = member("owner2@user-svc.test", true);
		Long adminId = member("admin2@user-svc.test", false, "US_PAYROLL");

		userService.update(adminId, "admin2.new@user-svc.test", null, ownerId);

		assertThat(userRepository.findById(adminId).orElseThrow().getEmail()).isEqualTo("admin2.new@user-svc.test");
	}
}
