package com.operion.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
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
class MembershipServiceTest {

	private MembershipService membershipService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	private Organisation organisation;

	@BeforeEach
	void setUp() {
		membershipService = new MembershipService(membershipRepository, userRepository, personRepository, roleRepository,
				campusRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));

		// A fresh unique slug per test method - Propagation.NOT_SUPPORTED means nothing
		// rolls back between methods in this class, so a fixed slug would collide on the
		// unique constraint from the second test onward.
		organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", "membership-svc-test-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void grantCreatesAnActiveMembership() {
		User user = userRepository.save(new User("grant@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Grant", "Ee"));
		Role role = roleRepository.save(new Role("Teacher", "teaches", false));

		OrganisationMembership membership = membershipService.grant(user.getId(), person.getId(), role.getId(), null);

		assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
		assertThat(membership.getCampus()).isNull();
	}

	@Test
	void grantAcceptsAnOptionalCampusScope() {
		User user = userRepository.save(new User("campus-grant@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Campus", "Scoped"));
		Role role = roleRepository.save(new Role("Front Desk", "desk", false));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));

		OrganisationMembership membership = membershipService.grant(user.getId(), person.getId(), role.getId(), campus.getId());

		assertThat(membership.getCampus().getId()).isEqualTo(campus.getId());
	}

	@Test
	void rejectsGrantingARoleTheUserAlreadyActivelyHolds() {
		User user = userRepository.save(new User("dup@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Dup", "Licate"));
		Role role = roleRepository.save(new Role("Accountant", "money", false));

		membershipService.grant(user.getId(), person.getId(), role.getId(), null);

		assertThatThrownBy(() -> membershipService.grant(user.getId(), person.getId(), role.getId(), null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void revokeFlipsStatusToInactiveAndPersistsIt() {
		User user = userRepository.save(new User("revoke@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Revoke", "Ee"));
		Role role = roleRepository.save(new Role("Teacher", "teaches", false));
		Long membershipId = membershipService.grant(user.getId(), person.getId(), role.getId(), null).getId();

		OrganisationMembership revoked = membershipService.revoke(membershipId);
		assertThat(revoked.getStatus()).isEqualTo(MembershipStatus.INACTIVE);

		// Re-query independently of the returned instance - proves the change actually
		// reached the database, not just the in-memory object handed back by the service.
		assertThat(membershipRepository.findById(membershipId).orElseThrow().getStatus()).isEqualTo(MembershipStatus.INACTIVE);
	}

	@Test
	void cannotRevokeTheLastActiveOrgAdminMembership() {
		User user = userRepository.save(new User("last-admin@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Last", "Admin"));
		Role adminRole = roleRepository.save(new Role("Org Admin", "system default", true));
		Long membershipId = membershipService.grant(user.getId(), person.getId(), adminRole.getId(), null).getId();

		assertThatThrownBy(() -> membershipService.revoke(membershipId)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void revokingOneOfTwoActiveOrgAdminsSucceeds() {
		Role adminRole = roleRepository.save(new Role("Org Admin", "system default", true));

		User firstAdminUser = userRepository.save(new User("admin1@example.com", null, "hash"));
		Person firstAdminPerson = personRepository.save(new Person("First", "Admin"));
		Long firstAdminMembershipId = membershipService.grant(firstAdminUser.getId(), firstAdminPerson.getId(), adminRole.getId(), null).getId();

		User secondAdminUser = userRepository.save(new User("admin2@example.com", null, "hash"));
		Person secondAdminPerson = personRepository.save(new Person("Second", "Admin"));
		membershipService.grant(secondAdminUser.getId(), secondAdminPerson.getId(), adminRole.getId(), null);

		OrganisationMembership revoked = membershipService.revoke(firstAdminMembershipId);

		assertThat(revoked.getStatus()).isEqualTo(MembershipStatus.INACTIVE);
	}
}
