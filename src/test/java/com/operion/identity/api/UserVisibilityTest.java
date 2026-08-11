package com.operion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * User is deliberately global (no organisation_id at all, see User's own javadoc) so it
 * can't be @TenantId-scoped the way every other entity is. UserController.list()/get()
 * derive their org boundary instead by joining through OrganisationMembership (which is
 * @TenantId-scoped) - this proves that join actually isolates per org, reproducing the
 * exact query UserController runs rather than going through the web layer (this codebase
 * has no MockMvc/@WebMvcTest precedent anywhere - see PermissionInterceptorTest for how
 * interceptor-level behaviour is tested at the service layer instead). Regression test for
 * a real bug: before this fix, GET /api/v1/users returned every login on the entire
 * platform - every organisation's users' emails - to any caller with MEMBERSHIP_MANAGE in
 * their own org alone.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserVisibilityTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private User userWithMembershipInOrg(Organisation organisation, String email) {
		User user = userRepository.save(new User(email, null, "hash"));
		Person person = personRepository.save(new Person("Test", "User"));
		Role role = roleRepository.save(new Role("Some Role", "desc", false));
		membershipRepository.save(new OrganisationMembership(user, person, role, null));
		return user;
	}

	@Test
	void listingUsersOnlyReturnsThoseWithAMembershipInTheCurrentOrg() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "user-vis-a"));
		TenantContext.set(orgA.getId(), null);
		User userA = userWithMembershipInOrg(orgA, "a@user-vis.test");

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "user-vis-b"));
		TenantContext.set(orgB.getId(), null);
		userWithMembershipInOrg(orgB, "b@user-vis.test");

		TenantContext.set(orgA.getId(), null);
		List<User> visibleToOrgA = membershipRepository.findAll().stream()
				.map(OrganisationMembership::getUser)
				.distinct()
				.toList();

		assertThat(visibleToOrgA).extracting(User::getId).containsExactly(userA.getId());
		assertThat(visibleToOrgA).extracting(User::getEmail).containsExactly("a@user-vis.test");
	}

	@Test
	void aUserWithNoMembershipAnywhereIsInvisibleToEveryOrg() {
		Organisation org = organisationRepository.save(new Organisation("C School", "C School Trust", "user-vis-c"));
		TenantContext.set(org.getId(), null);
		// Created but never granted a membership - the state a fresh POST /api/v1/users
		// leaves a login in until MembershipService.grant is also called.
		userRepository.save(new User("orphan@user-vis.test", null, "hash"));

		List<User> visible = membershipRepository.findAll().stream().map(OrganisationMembership::getUser).distinct().toList();

		assertThat(visible).isEmpty();
	}
}
