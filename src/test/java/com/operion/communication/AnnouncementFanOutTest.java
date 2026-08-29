package com.operion.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.authorization.MembershipStatus;
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
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves audience fan-out resolves the right Persons per AudienceType (SECTION -> a
 * current enrollment's student + guardians, ORG -> active memberships only), that a
 * disabled NotificationPreference excludes a person even though they're in the
 * audience, and that publish/cancel are one-way transitions off DRAFT - see
 * Announcement's class doc for why re-publish/cancel-after-publish are rejected rather
 * than silently re-fanning-out.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, ParentService.class, CommunicationService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AnnouncementFanOutTest {

	@Autowired
	private CommunicationService communicationService;

	@Autowired
	private ParentService parentService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private SectionRepository sectionRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private StudentEnrollmentRepository studentEnrollmentRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private OrganisationMembershipRepository organisationMembershipRepository;

	@Autowired
	private NotificationRecipientRepository notificationRecipientRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Campus campus, Section section, Student student, Person guardianPerson) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, "PRIMARY"));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, "Room 1"));

		Person studentPerson = personRepository.save(new Person("Ira", "Shah"));
		Student student = studentRepository.save(
				new Student(studentPerson, "STU-ADM-100", "ADM-100", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null));
		studentEnrollmentRepository.save(new StudentEnrollment(student, year, section, 1, LocalDate.of(2025, 6, 1)));

		Person guardianPerson = personRepository.save(new Person("Vikram", "Shah"));
		Guardian guardian = parentService.getOrCreateGuardian(guardianPerson, "Engineer");
		parentService.linkGuardian(student, guardian, GuardianRelationshipType.FATHER, true, true, true, true, 1);

		return new Fixture(campus, section, student, guardianPerson);
	}

	@Test
	void sectionAudiencePublishesToStudentAndGuardian() {
		Fixture fixture = setUpFixture("comm-section-fanout");

		Announcement announcement = communicationService.createAnnouncement(
				fixture.campus(), "PTM Reminder", "Meeting Friday 4pm", AudienceType.SECTION, fixture.section().getId());
		communicationService.publishAnnouncement(announcement);

		List<NotificationRecipient> recipients = notificationRecipientRepository.findByAnnouncementId(announcement.getId());
		assertThat(recipients).hasSize(2);
		assertThat(recipients).allSatisfy(recipient -> {
			assertThat(recipient.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
			assertThat(recipient.getChannel()).isEqualTo(NotificationChannel.IN_APP);
		});
		assertThat(recipients).extracting(recipient -> recipient.getPerson().getId())
				.containsExactlyInAnyOrder(fixture.student().getPerson().getId(), fixture.guardianPerson().getId());
	}

	@Test
	void disabledPreferenceExcludesPersonFromFanOut() {
		Fixture fixture = setUpFixture("comm-pref-fanout");
		communicationService.setPreference(fixture.guardianPerson(), NotificationChannel.IN_APP, false);

		Announcement announcement = communicationService.createAnnouncement(
				fixture.campus(), "PTM Reminder", "Meeting Friday 4pm", AudienceType.SECTION, fixture.section().getId());
		communicationService.publishAnnouncement(announcement);

		List<NotificationRecipient> recipients = notificationRecipientRepository.findByAnnouncementId(announcement.getId());
		assertThat(recipients).extracting(recipient -> recipient.getPerson().getId())
				.containsExactly(fixture.student().getPerson().getId());
	}

	@Test
	void orgAudiencePublishesToActiveMembersOnly() {
		setUpFixture("comm-org-fanout");

		Role role = roleRepository.save(new Role("Teacher", "Teacher role", false));

		User activeUser = userRepository.save(new User("teacher.active@example.com", null, "hash"));
		Person activePerson = personRepository.save(new Person("Asha", "Rao"));
		organisationMembershipRepository.save(new OrganisationMembership(activeUser, activePerson, role, null));

		User inactiveUser = userRepository.save(new User("teacher.inactive@example.com", null, "hash"));
		Person inactivePerson = personRepository.save(new Person("Rohit", "Nair"));
		OrganisationMembership inactiveMembership =
				organisationMembershipRepository.save(new OrganisationMembership(inactiveUser, inactivePerson, role, null));
		inactiveMembership.setStatus(MembershipStatus.INACTIVE);
		organisationMembershipRepository.save(inactiveMembership);

		Announcement announcement = communicationService.createAnnouncement(null, "Staff Meeting", "Monday 9am", AudienceType.ORG, null);
		communicationService.publishAnnouncement(announcement);

		List<NotificationRecipient> recipients = notificationRecipientRepository.findByAnnouncementId(announcement.getId());
		assertThat(recipients).extracting(recipient -> recipient.getPerson().getId()).containsExactly(activePerson.getId());
	}

	@Test
	void publishIsRejectedForANonDraftAnnouncement() {
		Fixture fixture = setUpFixture("comm-publish-guard");
		Announcement announcement = communicationService.createAnnouncement(
				fixture.campus(), "Notice", "Body", AudienceType.SECTION, fixture.section().getId());
		communicationService.publishAnnouncement(announcement);

		assertThatThrownBy(() -> communicationService.publishAnnouncement(announcement)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cancelIsRejectedAfterPublish() {
		Fixture fixture = setUpFixture("comm-cancel-guard");
		Announcement announcement = communicationService.createAnnouncement(
				fixture.campus(), "Notice", "Body", AudienceType.SECTION, fixture.section().getId());
		communicationService.publishAnnouncement(announcement);

		assertThatThrownBy(() -> communicationService.cancelAnnouncement(announcement)).isInstanceOf(IllegalStateException.class);
	}
}
