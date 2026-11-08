package com.operion.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Permission;
import com.operion.authorization.PermissionRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.auth.api.AuthController;
import com.operion.identity.auth.api.MeResponse;
import com.operion.modules.ModuleAccessService;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/v1/auth/me is what the frontend reads to decide whether to show Owner-only UI
 * (and which actions to hide), so the {@code owner} flag has to track the membership's
 * is_owner column exactly - and has to come back alongside, not instead of, the
 * role-granted {@code permissions} set (an Owner holds every permission via
 * PermissionChecker without explicit role grants, so the two are independent signals).
 * Drives the real AuthController.me() over the DataJpaTest repositories - this codebase
 * has no MockMvc precedent (see UserVisibilityTest) - with only the collaborators me()
 * never touches mocked.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeOwnerFlagTest {

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

	private AuthController authController() {
		// me() only reads memberships, users, organisations and module state; the rest are
		// the login/invite/reset flows, so mocks are enough to satisfy the constructor.
		ModuleAccessService moduleAccessService = mock(ModuleAccessService.class);
		when(moduleAccessService.disabledModules(anyLong())).thenReturn(Set.of());
		return new AuthController(mock(AuthenticationService.class), membershipRepository, userRepository, organisationRepository,
				mock(StaffInviteService.class), mock(RefreshTokenService.class), mock(PasswordResetService.class),
				mock(EmailVerificationService.class), moduleAccessService, mock(AuthRateLimiter.class));
	}

	private Organisation newOrganisation() {
		Organisation organisation = organisationRepository
				.save(new Organisation("Me School", "Me School Trust", "me-owner-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		return organisation;
	}

	/** Saves a user with one ACTIVE membership in the current tenant, on a role holding the
	 * given permission code (or no permissions when null), flagged owner or not. Returns the user id. */
	private Long activeMember(boolean owner, String permissionCode) {
		User user = userRepository.save(new User("me-" + System.nanoTime() + "@me-owner.test", null, "hash"));
		Person person = personRepository.save(new Person("Me", "Member"));
		Role role = new Role("Me Role " + System.nanoTime(), "desc", false);
		if (permissionCode != null) {
			role.grant(permissionRepository.findByCode(permissionCode)
					.orElseGet(() -> permissionRepository.save(new Permission(permissionCode, "test", null))));
		}
		role = roleRepository.save(role);
		membershipRepository.save(new OrganisationMembership(user, person, role, null, null, owner));
		return user.getId();
	}

	private MeResponse meAs(Organisation organisation, Long userId) {
		TenantContext.set(organisation.getId(), userId);
		return authController().me();
	}

	@Test
	void anActiveOwnerMembershipReturnsOwnerTrueAlongsideItsRolePermissions() {
		Organisation organisation = newOrganisation();
		String code = "ME_OWNER_VIEW_" + System.nanoTime();
		Long ownerId = activeMember(true, code);

		MeResponse me = meAs(organisation, ownerId);

		assertThat(me.owner()).isTrue();
		// permissions is still populated for an Owner - the flag doesn't replace the set.
		assertThat(me.permissions()).containsExactly(code);
		assertThat(me.userId()).isEqualTo(ownerId);
		assertThat(me.organisationId()).isEqualTo(organisation.getId());
	}

	@Test
	void anActiveNonOwnerMemberReturnsOwnerFalseAlongsideItsRolePermissions() {
		Organisation organisation = newOrganisation();
		String code = "ME_MEMBER_VIEW_" + System.nanoTime();
		Long memberId = activeMember(false, code);

		MeResponse me = meAs(organisation, memberId);

		assertThat(me.owner()).isFalse();
		assertThat(me.permissions()).containsExactly(code);
	}

	@Test
	void aNonOwnerWithNoRolePermissionsGetsOwnerFalseAndAnEmptyPermissionSet() {
		Organisation organisation = newOrganisation();
		Long memberId = activeMember(false, null);

		MeResponse me = meAs(organisation, memberId);

		assertThat(me.owner()).isFalse();
		assertThat(me.permissions()).isEmpty();
	}

	@Test
	void ownerIsResolvedPerOrganisationNotAcrossOrganisations() {
		// Org A's Owner and org B's plain member, each answered by /me scoped to their own org -
		// one org having an Owner must not make another org's non-owner report owner=true
		// (@TenantId on OrganisationMembership is what keeps the lookup per-org).
		Organisation orgA = newOrganisation();
		Long ownerA = activeMember(true, null);
		Organisation orgB = newOrganisation();
		Long memberB = activeMember(false, null);

		assertThat(meAs(orgA, ownerA).owner()).isTrue();
		assertThat(meAs(orgB, memberB).owner()).isFalse();
	}
}
