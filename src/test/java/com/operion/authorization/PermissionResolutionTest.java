package com.operion.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves OrganisationMembershipRepository.findActivePermissionCodesForUser - the query
 * PermissionInterceptor relies on for every gated request - unions permissions across a
 * user's multiple ACTIVE memberships, ignores INACTIVE memberships/roles, and stays
 * tenant-isolated even for the same user id across two orgs (same convention as every
 * other *TenantIsolationTest in this codebase).
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PermissionResolutionTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PermissionRepository permissionRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void unionsPermissionsAcrossActiveMembershipsAndIgnoresInactiveOnes() {
		Organisation org = organisationRepository.save(new Organisation("Test School", "Test School Trust", "test-school"));
		User user = userRepository.save(new User("multi-role@example.com", null, "hash"));

		TenantContext.set(org.getId(), user.getId());

		Permission viewPermission = permissionRepository.save(new Permission("TEST_VIEW", "test", null));
		Permission managePermission = permissionRepository.save(new Permission("TEST_MANAGE", "test", null));
		Permission inactiveRolePermission = permissionRepository.save(new Permission("TEST_INACTIVE_ROLE_ONLY", "test", null));
		Permission inactiveMembershipPermission = permissionRepository.save(
				new Permission("TEST_INACTIVE_MEMBERSHIP_ONLY", "test", null));

		Role viewerRole = new Role("Viewer", "view only", false);
		viewerRole.grant(viewPermission);
		viewerRole = roleRepository.save(viewerRole);

		Role managerRole = new Role("Manager", "manage", false);
		managerRole.grant(managePermission);
		managerRole = roleRepository.save(managerRole);

		Role inactiveRole = new Role("Retired Role", "no longer usable", false);
		inactiveRole.grant(inactiveRolePermission);
		inactiveRole.setStatus(RoleStatus.INACTIVE);
		inactiveRole = roleRepository.save(inactiveRole);

		Role roleForRevokedMembership = new Role("Revoked Membership Role", "was granted, then revoked", false);
		roleForRevokedMembership.grant(inactiveMembershipPermission);
		roleForRevokedMembership = roleRepository.save(roleForRevokedMembership);

		Person person = personRepository.save(new Person("Multi", "Role"));

		membershipRepository.save(new OrganisationMembership(user, person, viewerRole, null));
		membershipRepository.save(new OrganisationMembership(user, person, managerRole, null));
		membershipRepository.save(new OrganisationMembership(user, person, inactiveRole, null));

		OrganisationMembership revoked = new OrganisationMembership(user, person, roleForRevokedMembership, null);
		revoked.setStatus(MembershipStatus.INACTIVE);
		membershipRepository.save(revoked);

		Set<String> resolved = membershipRepository.findActivePermissionCodesForUser(user.getId());

		assertThat(resolved).containsExactlyInAnyOrder("TEST_VIEW", "TEST_MANAGE");
	}

	@Test
	void neverLeaksPermissionsAcrossOrganisationsForTheSameUser() {
		Organisation orgA = organisationRepository.save(new Organisation("Org A", "Org A Trust", "org-a-perm"));
		Organisation orgB = organisationRepository.save(new Organisation("Org B", "Org B Trust", "org-b-perm"));
		User user = userRepository.save(new User("cross-org@example.com", null, "hash"));

		TenantContext.set(orgA.getId(), user.getId());
		Permission orgAPermission = permissionRepository.save(new Permission("ORG_A_ONLY", "test", null));
		Role orgARole = new Role("Org A Role", "org a", false);
		orgARole.grant(orgAPermission);
		orgARole = roleRepository.save(orgARole);
		Person personInA = personRepository.save(new Person("A", "Person"));
		membershipRepository.save(new OrganisationMembership(user, personInA, orgARole, null));

		TenantContext.set(orgB.getId(), user.getId());
		Set<String> resolvedInOrgB = membershipRepository.findActivePermissionCodesForUser(user.getId());

		assertThat(resolvedInOrgB).isEmpty();
	}
}
