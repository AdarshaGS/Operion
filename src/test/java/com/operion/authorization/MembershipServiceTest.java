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
import com.operion.organisation.DepartmentRepository;
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
	private DepartmentRepository departmentRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private PermissionRepository permissionRepository;

	private Organisation organisation;

	@BeforeEach
	void setUp() {
		membershipService = new MembershipService(membershipRepository, userRepository, personRepository, roleRepository,
				campusRepository, departmentRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));

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

		OrganisationMembership membership = membershipService.grant(user.getId(), person.getId(), role.getId(), null, null);

		assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
		assertThat(membership.getMemberId()).isNull();
		assertThat(membership.getJoiningDate()).isNull();
	}

	/** A member who manages memberships, holding {@code actorCodes}, plus a target role carrying {@code targetCodes}. */
	private Long actorHolding(String... actorCodes) {
		Role actorRole = new Role("Office " + System.nanoTime(), "manages members", false);
		for (String code : actorCodes) {
			actorRole.grant(permission(code));
		}
		actorRole = roleRepository.save(actorRole);
		User actor = userRepository.save(new User("actor-" + System.nanoTime() + "@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Office", "Manager"));
		membershipRepository.save(new OrganisationMembership(actor, person, actorRole, null));
		return actor.getId();
	}

	private Permission permission(String code) {
		return permissionRepository.findAll().stream().filter(p -> p.getCode().equals(code)).findFirst()
				.orElseGet(() -> permissionRepository.save(new Permission(code, "test", null)));
	}

	@Test
	void anActorCannotGrantARoleCarryingPermissionsTheyDoNotHold() {
		Long actorId = actorHolding("MEMBERSHIP_MANAGE", "MS_LOW");
		Role adminLike = new Role("Admin-like", "more than the actor has", false);
		adminLike.grant(permission("MS_LOW"));
		adminLike.grant(permission("ALL_FUNCTIONS"));
		Long roleId = roleRepository.save(adminLike).getId();
		User target = userRepository.save(new User("escalate@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Esca", "Late"));

		assertThatThrownBy(() -> membershipService.grant(target.getId(), person.getId(), roleId, null, null, null, null, actorId))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThat(membershipRepository.findByUserId(target.getId())).isEmpty();
	}

	@Test
	void anActorCanGrantARoleWhosePermissionsTheyHold() {
		Long actorId = actorHolding("MEMBERSHIP_MANAGE", "MS_LOW", "MS_MID");
		Role teacher = new Role("Teacher", "teaches", false);
		teacher.grant(permission("MS_LOW"));
		Long roleId = roleRepository.save(teacher).getId();
		User target = userRepository.save(new User("allowed@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Al", "Lowed"));

		membershipService.grant(target.getId(), person.getId(), roleId, null, null, null, null, actorId);

		assertThat(membershipRepository.findByUserId(target.getId())).hasSize(1);
	}

	@Test
	void anAllFunctionsHolderCanGrantAnyRole() {
		Long actorId = actorHolding("ALL_FUNCTIONS");
		Role role = new Role("Bursar", "money", false);
		role.grant(permission("MS_HIGH"));
		Long roleId = roleRepository.save(role).getId();
		User target = userRepository.save(new User("bursar@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Bur", "Sar"));

		membershipService.grant(target.getId(), person.getId(), roleId, null, null, null, null, actorId);

		assertThat(membershipRepository.findByUserId(target.getId())).hasSize(1);
	}

	@Test
	void grantStoresOptionalMemberIdAndJoiningDate() {
		User user = userRepository.save(new User("grant-with-id@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Grant", "WithId"));
		Role role = roleRepository.save(new Role("Teacher", "teaches", false));

		OrganisationMembership membership = membershipService.grant(user.getId(), person.getId(), role.getId(), null, null,
				"EMP-042", java.time.LocalDate.of(2026, 1, 15));

		assertThat(membership.getMemberId()).isEqualTo("EMP-042");
		assertThat(membership.getJoiningDate()).isEqualTo(java.time.LocalDate.of(2026, 1, 15));
		assertThat(membership.getCampus()).isNull();
	}

	@Test
	void grantAcceptsAnOptionalCampusScope() {
		User user = userRepository.save(new User("campus-grant@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Campus", "Scoped"));
		Role role = roleRepository.save(new Role("Front Desk", "desk", false));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));

		OrganisationMembership membership = membershipService.grant(user.getId(), person.getId(), role.getId(), campus.getId(), null);

		assertThat(membership.getCampus().getId()).isEqualTo(campus.getId());
	}

	@Test
	void rejectsGrantingARoleTheUserAlreadyActivelyHolds() {
		User user = userRepository.save(new User("dup@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Dup", "Licate"));
		Role role = roleRepository.save(new Role("Accountant", "money", false));

		membershipService.grant(user.getId(), person.getId(), role.getId(), null, null);

		assertThatThrownBy(() -> membershipService.grant(user.getId(), person.getId(), role.getId(), null, null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void revokeFlipsStatusToInactiveAndPersistsIt() {
		User user = userRepository.save(new User("revoke@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Revoke", "Ee"));
		Role role = roleRepository.save(new Role("Teacher", "teaches", false));
		Long membershipId = membershipService.grant(user.getId(), person.getId(), role.getId(), null, null).getId();

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
		Long membershipId = membershipService.grant(user.getId(), person.getId(), adminRole.getId(), null, null).getId();

		assertThatThrownBy(() -> membershipService.revoke(membershipId)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void revokingOneOfTwoActiveOrgAdminsSucceeds() {
		Role adminRole = roleRepository.save(new Role("Org Admin", "system default", true));

		User firstAdminUser = userRepository.save(new User("admin1@example.com", null, "hash"));
		Person firstAdminPerson = personRepository.save(new Person("First", "Admin"));
		Long firstAdminMembershipId = membershipService.grant(firstAdminUser.getId(), firstAdminPerson.getId(), adminRole.getId(), null, null).getId();

		User secondAdminUser = userRepository.save(new User("admin2@example.com", null, "hash"));
		Person secondAdminPerson = personRepository.save(new Person("Second", "Admin"));
		membershipService.grant(secondAdminUser.getId(), secondAdminPerson.getId(), adminRole.getId(), null, null);

		OrganisationMembership revoked = membershipService.revoke(firstAdminMembershipId);

		assertThat(revoked.getStatus()).isEqualTo(MembershipStatus.INACTIVE);
	}

	@Test
	void cannotRevokeTheOwnerMembershipEvenWithAnotherActiveAdminPresent() {
		Role adminRole = roleRepository.save(new Role("Owner", "system default", true));

		User ownerUser = userRepository.save(new User("owner@example.com", null, "hash"));
		Person ownerPerson = personRepository.save(new Person("The", "Owner"));
		Long ownerMembershipId = membershipRepository
				.save(new OrganisationMembership(ownerUser, ownerPerson, adminRole, null, null, true)).getId();

		User secondAdminUser = userRepository.save(new User("second-admin@example.com", null, "hash"));
		Person secondAdminPerson = personRepository.save(new Person("Second", "Admin"));
		membershipService.grant(secondAdminUser.getId(), secondAdminPerson.getId(), adminRole.getId(), null, null);

		assertThatThrownBy(() -> membershipService.revoke(ownerMembershipId)).isInstanceOf(IllegalStateException.class);
	}
}
