package com.operion.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentType;
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
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRelationshipType;
import com.operion.parent.ParentService;
import com.operion.student.Student;
import com.operion.student.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers #206: GET /api/v1/me's routing decision - Role#isPortal() gates admin vs.
 * portal, Owner always wins regardless of that flag, and Guardian/Teacher contexts are
 * populated independently (a person can hold both).
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, ParentService.class, PortalIdentityService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PortalIdentityServiceTest {

	@Autowired
	private PortalIdentityService portalIdentityService;

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
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private SectionRepository sectionRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private TeacherAssignmentRepository teacherAssignmentRepository;

	@Autowired
	private ParentService parentService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Organisation setUpOrganisation(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		return organisation;
	}

	@Test
	void nonPortalRoleLandsInAdminSession() {
		setUpOrganisation("portal-admin-role");
		User user = userRepository.save(new User("admin-role@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Ann", "Admin"));
		Role role = roleRepository.save(new Role("Front Desk", "desk", false));
		membershipRepository.save(new OrganisationMembership(user, person, role, null));

		MeResponse response = portalIdentityService.resolve(user.getId());

		assertThat(response.sessionType()).isEqualTo("ADMIN");
		assertThat(response.portalContexts()).isEmpty();
	}

	@Test
	void ownerAlwaysLandsInAdminSessionEvenWithAPortalRole() {
		setUpOrganisation("portal-owner");
		User user = userRepository.save(new User("owner@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Olive", "Owner"));
		Role portalRole = roleRepository.save(new Role("Owner-Portal", "portal-flagged but owner", false, false, true));
		membershipRepository.save(new OrganisationMembership(user, person, portalRole, null, null, true));

		MeResponse response = portalIdentityService.resolve(user.getId());

		assertThat(response.sessionType()).isEqualTo("ADMIN");
	}

	@Test
	void guardianWithPortalRoleGetsAGuardianContextWithLinkedChildren() {
		setUpOrganisation("portal-guardian");
		User user = userRepository.save(new User("guardian@example.com", null, "hash"));
		Person guardianPerson = personRepository.save(new Person("Vikram", "Shah"));
		Role portalRole = roleRepository.save(new Role("Guardian", "portal access", false, true, true));
		membershipRepository.save(new OrganisationMembership(user, guardianPerson, portalRole, null));

		Person studentPerson = personRepository.save(new Person("Ira", "Shah"));
		Student student = studentRepository.save(new Student(studentPerson, "STU-300", "ADM-300", LocalDate.of(2025, 5, 1), null, null,
				null, null, null, null, null, null, null, null, null, null));
		Guardian guardian = parentService.getOrCreateGuardian(guardianPerson, "Engineer");
		parentService.linkGuardian(student, guardian, GuardianRelationshipType.FATHER, true, true, true, true, 1);

		MeResponse response = portalIdentityService.resolve(user.getId());

		assertThat(response.sessionType()).isEqualTo("PORTAL");
		assertThat(response.portalContexts()).hasSize(1);
		PortalContext context = response.portalContexts().get(0);
		assertThat(context.type()).isEqualTo(PortalContextType.GUARDIAN);
		assertThat(context.guardianId()).isEqualTo(guardian.getId());
		assertThat(context.children()).extracting(ChildSummary::studentId).containsExactly(student.getId());
		assertThat(context.children()).extracting(ChildSummary::name).containsExactly("Ira Shah");
	}

	@Test
	void teacherWithPortalRoleGetsATeacherContext() {
		setUpOrganisation("portal-teacher");
		User user = userRepository.save(new User("teacher@example.com", null, "hash"));
		Person teacherPerson = personRepository.save(new Person("Meera", "Nair"));
		Role portalRole = roleRepository.save(new Role("Teacher", "portal access", false, false, true));
		membershipRepository.save(new OrganisationMembership(user, teacherPerson, portalRole, null));

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, "PRIMARY"));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, "Room 1"));
		teacherAssignmentRepository.save(
				new TeacherAssignment(year, section, null, teacherPerson, TeacherAssignmentType.HOMEROOM, LocalDate.of(2025, 6, 1)));

		MeResponse response = portalIdentityService.resolve(user.getId());

		assertThat(response.sessionType()).isEqualTo("PORTAL");
		assertThat(response.portalContexts()).hasSize(1);
		PortalContext context = response.portalContexts().get(0);
		assertThat(context.type()).isEqualTo(PortalContextType.TEACHER);
		assertThat(context.teacherPersonId()).isEqualTo(teacherPerson.getId());
	}

	@Test
	void personHoldingBothPortalRolesGetsBothContexts() {
		setUpOrganisation("portal-both");
		User user = userRepository.save(new User("both@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Meera", "Nair"));
		Role guardianRole = roleRepository.save(new Role("Guardian", "portal access", false, true, true));
		Role teacherRole = roleRepository.save(new Role("Teacher", "portal access", false, false, true));
		membershipRepository.save(new OrganisationMembership(user, person, guardianRole, null));
		membershipRepository.save(new OrganisationMembership(user, person, teacherRole, null));

		Person studentPerson = personRepository.save(new Person("Ira", "Shah"));
		Student student = studentRepository.save(new Student(studentPerson, "STU-301", "ADM-301", LocalDate.of(2025, 5, 1), null, null,
				null, null, null, null, null, null, null, null, null, null));
		Guardian guardian = parentService.getOrCreateGuardian(person, "Engineer");
		parentService.linkGuardian(student, guardian, GuardianRelationshipType.FATHER, true, true, true, true, 1);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, "PRIMARY"));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, "Room 1"));
		teacherAssignmentRepository.save(
				new TeacherAssignment(year, section, null, person, TeacherAssignmentType.HOMEROOM, LocalDate.of(2025, 6, 1)));

		MeResponse response = portalIdentityService.resolve(user.getId());

		assertThat(response.sessionType()).isEqualTo("PORTAL");
		assertThat(response.portalContexts()).extracting(PortalContext::type)
				.containsExactlyInAnyOrder(PortalContextType.GUARDIAN, PortalContextType.TEACHER);
	}

	@Test
	void noActiveMembershipLandsInAdminSession() {
		setUpOrganisation("portal-no-membership");
		User user = userRepository.save(new User("nomembership@example.com", null, "hash"));

		MeResponse response = portalIdentityService.resolve(user.getId());

		assertThat(response.sessionType()).isEqualTo("ADMIN");
		assertThat(response.portalContexts()).isEmpty();
	}
}
