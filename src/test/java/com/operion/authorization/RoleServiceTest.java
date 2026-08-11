package com.operion.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

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

@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoleServiceTest {

	private RoleService roleService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PermissionRepository permissionRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@BeforeEach
	void setUp() {
		roleService = new RoleService(roleRepository, permissionRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));

		// A fresh unique slug per test method - Propagation.NOT_SUPPORTED means nothing
		// rolls back between methods in this class, so a fixed slug would collide on the
		// unique constraint from the second test onward.
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", "role-svc-test-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	/** Permission.code is a genuinely global unique catalog (not tenant-scoped), and
	 * Propagation.NOT_SUPPORTED means nothing rolls back between test methods in this
	 * class - so any test touching a fixed real code (ROLE_MANAGE/MEMBERSHIP_MANAGE) must
	 * reuse a prior insert rather than risk a duplicate, and can't assume execution order. */
	private Permission findOrCreatePermission(String code) {
		return permissionRepository.findAll().stream()
				.filter(p -> p.getCode().equals(code))
				.findFirst()
				.orElseGet(() -> permissionRepository.save(new Permission(code, "test", null)));
	}

	@Test
	void createGrantsExactlyTheRequestedPermissions() {
		permissionRepository.save(new Permission("RS_VIEW", "test", null));
		permissionRepository.save(new Permission("RS_MANAGE", "test", null));
		permissionRepository.save(new Permission("RS_UNUSED", "test", null));

		Role role = roleService.create("Custom Role", "a custom role", Set.of("RS_VIEW", "RS_MANAGE"));

		assertThat(role.getPermissions()).extracting(Permission::getCode).containsExactlyInAnyOrder("RS_VIEW", "RS_MANAGE");
	}

	@Test
	void createRejectsAnUnknownPermissionCode() {
		assertThatThrownBy(() -> roleService.create("Bad Role", "desc", Set.of("DOES_NOT_EXIST")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void updatePermissionsSwapsTheFullSetAndPersistsIt() {
		Permission keep = permissionRepository.save(new Permission("RS_KEEP", "test", null));
		Permission drop = permissionRepository.save(new Permission("RS_DROP", "test", null));
		permissionRepository.save(new Permission("RS_ADD", "test", null));

		Role role = new Role("Swappable", "desc", false);
		role.grant(keep);
		role.grant(drop);
		Long roleId = roleRepository.save(role).getId();

		Role updated = roleService.updatePermissions(roleId, Set.of("RS_KEEP", "RS_ADD"));
		assertThat(updated.getPermissions()).extracting(Permission::getCode).containsExactlyInAnyOrder("RS_KEEP", "RS_ADD");

		// Re-query independently of the returned instance - proves the change actually
		// reached the database, not just the in-memory object handed back by the service.
		Role reloaded = roleRepository.findById(roleId).orElseThrow();
		assertThat(reloaded.getPermissions()).extracting(Permission::getCode).containsExactlyInAnyOrder("RS_KEEP", "RS_ADD");
	}

	@Test
	void cannotStripTheLastPermissionFromASystemDefaultRole() {
		Permission onlyPermission = permissionRepository.save(new Permission("RS_ONLY", "test", null));
		Role admin = new Role("Org Admin", "system default", true);
		admin.grant(onlyPermission);
		Long adminId = roleRepository.save(admin).getId();

		assertThatThrownBy(() -> roleService.updatePermissions(adminId, Set.of()))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cannotRevokeRoleManageFromASystemDefaultRoleEvenWithOtherPermissionsRemaining() {
		Permission roleManage = findOrCreatePermission("ROLE_MANAGE");
		Permission other = permissionRepository.save(new Permission("RS_OTHER", "test", null));
		Role admin = new Role("Org Admin", "system default", true);
		admin.grant(roleManage);
		admin.grant(other);
		Long adminId = roleRepository.save(admin).getId();

		// Dropping ROLE_MANAGE while RS_OTHER stays granted - the plain "never reach zero
		// permissions" guard alone wouldn't catch this, since the role still ends up with
		// one permission left, just not the one that matters.
		assertThatThrownBy(() -> roleService.updatePermissions(adminId, Set.of("RS_OTHER")))
				.isInstanceOf(IllegalStateException.class);
		assertThat(roleRepository.findById(adminId).orElseThrow().getPermissions())
				.extracting(Permission::getCode)
				.containsExactlyInAnyOrder("ROLE_MANAGE", "RS_OTHER");
	}

	@Test
	void cannotRevokeMembershipManageFromASystemDefaultRoleEvenWithOtherPermissionsRemaining() {
		Permission membershipManage = findOrCreatePermission("MEMBERSHIP_MANAGE");
		Permission other = permissionRepository.save(new Permission("RS_OTHER2", "test", null));
		Role admin = new Role("Org Admin", "system default", true);
		admin.grant(membershipManage);
		admin.grant(other);
		Long adminId = roleRepository.save(admin).getId();

		assertThatThrownBy(() -> roleService.updatePermissions(adminId, Set.of("RS_OTHER2")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void roleManageAndMembershipManageCanStillBeRevokedFromANonDefaultRole() {
		Permission roleManage = findOrCreatePermission("ROLE_MANAGE");
		Role custom = new Role("Custom Admin-ish", "not the system default", false);
		custom.grant(roleManage);
		Long customId = roleRepository.save(custom).getId();

		Role updated = roleService.updatePermissions(customId, Set.of());
		assertThat(updated.getPermissions()).isEmpty();
	}

	@Test
	void cannotDeactivateTheSystemDefaultRole() {
		Long adminId = roleRepository.save(new Role("Org Admin", "system default", true)).getId();

		assertThatThrownBy(() -> roleService.changeStatus(adminId, RoleStatus.INACTIVE))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aNonDefaultRoleCanBeDeactivatedAndReactivatedAndItPersists() {
		Long roleId = roleRepository.save(new Role("Front Desk", "desk", false)).getId();

		Role deactivated = roleService.changeStatus(roleId, RoleStatus.INACTIVE);
		assertThat(deactivated.getStatus()).isEqualTo(RoleStatus.INACTIVE);
		assertThat(roleRepository.findById(roleId).orElseThrow().getStatus()).isEqualTo(RoleStatus.INACTIVE);

		Role reactivated = roleService.changeStatus(roleId, RoleStatus.ACTIVE);
		assertThat(reactivated.getStatus()).isEqualTo(RoleStatus.ACTIVE);
		assertThat(roleRepository.findById(roleId).orElseThrow().getStatus()).isEqualTo(RoleStatus.ACTIVE);
	}
}
