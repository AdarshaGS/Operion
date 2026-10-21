package com.operion.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentType;
import com.operion.attendance.AttendanceAccessService.MarkScope;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Permission;
import com.operion.authorization.PermissionChecker;
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
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Plain ATTENDANCE_MARK is homeroom-only; ATTENDANCE_MARK_ANY_SECTION and Owners cover every section. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, PermissionChecker.class, AttendanceAccessService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AttendanceAccessServiceTest {

	@Autowired private AttendanceAccessService accessService;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private RoleRepository roleRepository;
	@Autowired private PermissionRepository permissionRepository;
	@Autowired private OrganisationMembershipRepository membershipRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private AcademicYearRepository academicYearRepository;
	@Autowired private GradeLevelRepository gradeLevelRepository;
	@Autowired private SchoolClassRepository schoolClassRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private SubjectRepository subjectRepository;
	@Autowired private TeacherAssignmentRepository teacherAssignmentRepository;

	private Section sectionA;
	private Section sectionB;
	private AcademicYear year;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void setUp(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Trust", slug));
		TenantContext.set(organisation.getId(), null);
		Campus campus = campusRepository.save(new Campus("Main", "MAIN-" + slug));
		year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, "PRIMARY"));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		sectionA = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		sectionB = sectionRepository.save(new Section(schoolClass, "B", 40, null));
	}

	private Permission permission(String code) {
		return permissionRepository.findByCode(code)
				.orElseGet(() -> permissionRepository.save(new Permission(code, "attendance", null)));
	}

	private User member(String email, Person person, String... codes) {
		User user = userRepository.save(new User(email, null, "hash"));
		Role role = new Role("Role " + email, null, false);
		for (String code : codes) {
			role.grant(permission(code));
		}
		role = roleRepository.save(role);
		membershipRepository.save(new OrganisationMembership(user, person, role, null));
		return user;
	}

	@Test
	void classTeacherMarksOnlyTheirHomeroomSection() {
		setUp("att-scope-homeroom");
		Person teacher = personRepository.save(new Person("Tara", "Teacher"));
		teacherAssignmentRepository.save(
				new TeacherAssignment(year, sectionA, null, teacher, TeacherAssignmentType.HOMEROOM, LocalDate.of(2025, 6, 1)));
		User user = member("tara@example.com", teacher, "ATTENDANCE_MARK");

		var markable = accessService.markableSections(user.getId());
		assertThat(markable.scope()).isEqualTo(MarkScope.OWN);
		assertThat(markable.sectionIds()).containsExactly(sectionA.getId());

		accessService.requireCanMark(user.getId(), sectionA);
		assertThatThrownBy(() -> accessService.requireCanMark(user.getId(), sectionB))
				.isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void subjectTeacherAssignmentDoesNotGrantMarking() {
		setUp("att-scope-subject");
		Person teacher = personRepository.save(new Person("Sam", "Subject"));
		Subject maths = subjectRepository.save(new Subject("Mathematics", "MATH-att"));
		teacherAssignmentRepository.save(
				new TeacherAssignment(year, sectionA, maths, teacher, TeacherAssignmentType.SUBJECT, LocalDate.of(2025, 6, 1)));
		User user = member("sam@example.com", teacher, "ATTENDANCE_MARK");

		assertThat(accessService.markableSections(user.getId()).sectionIds()).isEmpty();
		assertThatThrownBy(() -> accessService.requireCanMark(user.getId(), sectionA))
				.isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void anySectionPermissionMarksEverySection() {
		setUp("att-scope-any");
		Person clerk = personRepository.save(new Person("Olu", "Office"));
		User user = member("olu@example.com", clerk, "ATTENDANCE_MARK_ANY_SECTION");

		assertThat(accessService.markableSections(user.getId()).scope()).isEqualTo(MarkScope.ALL);
		accessService.requireCanMark(user.getId(), sectionB);
	}

	@Test
	void ownerMarksEverySection() {
		setUp("att-scope-owner");
		User user = userRepository.save(new User("owner-att@example.com", null, "hash"));
		Person person = personRepository.save(new Person("Ola", "Owner"));
		Role role = roleRepository.save(new Role("Owner role", null, true));
		membershipRepository.save(new OrganisationMembership(user, person, role, null, null, true));

		assertThat(accessService.markableSections(user.getId()).scope()).isEqualTo(MarkScope.ALL);
	}

	@Test
	void noMarkPermissionMeansNone() {
		setUp("att-scope-none");
		Person viewer = personRepository.save(new Person("Vic", "Viewer"));
		User user = member("vic@example.com", viewer, "ATTENDANCE_VIEW");

		assertThat(accessService.markableSections(user.getId()).scope()).isEqualTo(MarkScope.NONE);
		assertThatThrownBy(() -> accessService.requireCanMark(user.getId(), sectionA))
				.isInstanceOf(AuthorizationDeniedException.class);
	}
}
