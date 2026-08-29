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
import com.operion.hr.EmploymentType;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.hr.StaffProfileStatus;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
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

	@Autowired
	private DesignationRepository designationRepository;

	@Autowired
	private StaffProfileRepository staffProfileRepository;

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
				new Student(studentPerson, "ADM-100", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null));
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
	void publishFansOutToEmailWhenThePersonHasAnAddress() {
		Fixture fixture = setUpFixture("comm-email-fanout");
		fixture.guardianPerson().setEmail("vikram@example.com");
		personRepository.save(fixture.guardianPerson());

		Announcement announcement = communicationService.createAnnouncement(
				fixture.campus(), "PTM Reminder", "Meeting Friday 4pm", AudienceType.SECTION, fixture.section().getId());
		communicationService.publishAnnouncement(announcement);

		List<NotificationRecipient> recipients = notificationRecipientRepository.findByAnnouncementId(announcement.getId());
		assertThat(recipients).hasSize(3); // student IN_APP, guardian IN_APP, guardian EMAIL - no phone on file for either

		NotificationRecipient guardianEmail = recipients.stream()
				.filter(r -> r.getChannel() == NotificationChannel.EMAIL)
				.findFirst().orElseThrow();
		assertThat(guardianEmail.getPerson().getId()).isEqualTo(fixture.guardianPerson().getId());
		assertThat(guardianEmail.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
		assertThat(guardianEmail.getSubject()).isEqualTo("PTM Reminder");
		assertThat(guardianEmail.getBody()).isEqualTo("Meeting Friday 4pm");
	}

	@Test
	void publishSkipsEmailWhenTheChannelIsDisabledEvenWithAnAddressOnFile() {
		Fixture fixture = setUpFixture("comm-email-disabled-fanout");
		fixture.guardianPerson().setEmail("vikram@example.com");
		personRepository.save(fixture.guardianPerson());
		communicationService.setPreference(fixture.guardianPerson(), NotificationChannel.EMAIL, false);

		Announcement announcement = communicationService.createAnnouncement(
				fixture.campus(), "PTM Reminder", "Meeting Friday 4pm", AudienceType.SECTION, fixture.section().getId());
		communicationService.publishAnnouncement(announcement);

		List<NotificationRecipient> recipients = notificationRecipientRepository.findByAnnouncementId(announcement.getId());
		assertThat(recipients).noneMatch(r -> r.getChannel() == NotificationChannel.EMAIL);
	}

	@Test
	void staffAudiencePublishesToActiveStaffOnly() {
		setUpFixture("comm-staff-fanout");
		Designation designation = designationRepository.save(new Designation("Teacher"));

		Person activeStaffPerson = personRepository.save(new Person("Meera", "Iyer"));
		StaffProfile activeStaff = staffProfileRepository.save(new StaffProfile(
				activeStaffPerson, null, "EMP-1", designation, null, LocalDate.of(2020, 6, 1), EmploymentType.PERMANENT));

		Person inactiveStaffPerson = personRepository.save(new Person("Kabir", "Sen"));
		StaffProfile inactiveStaff = staffProfileRepository.save(new StaffProfile(
				inactiveStaffPerson, null, "EMP-2", designation, null, LocalDate.of(2020, 6, 1), EmploymentType.PERMANENT));
		inactiveStaff.changeStatus(StaffProfileStatus.RESIGNED);
		staffProfileRepository.save(inactiveStaff);

		Announcement announcement = communicationService.createAnnouncement(null, "Staff Briefing", "Body", AudienceType.STAFF, null);
		communicationService.publishAnnouncement(announcement);

		List<NotificationRecipient> recipients = notificationRecipientRepository.findByAnnouncementId(announcement.getId());
		assertThat(recipients).extracting(recipient -> recipient.getPerson().getId()).containsExactly(activeStaffPerson.getId());
	}

	@Test
	void staffMemberAudiencePublishesToOneStaffPerson() {
		setUpFixture("comm-staff-member-fanout");
		Designation designation = designationRepository.save(new Designation("Teacher"));
		Person staffPerson = personRepository.save(new Person("Nikhil", "Rao"));
		StaffProfile staffProfile = staffProfileRepository.save(new StaffProfile(
				staffPerson, null, "EMP-3", designation, null, LocalDate.of(2020, 6, 1), EmploymentType.PERMANENT));

		Announcement announcement = communicationService.createAnnouncement(
				null, "Performance Review", "Body", AudienceType.STAFF_MEMBER, staffProfile.getId());
		communicationService.publishAnnouncement(announcement);

		List<NotificationRecipient> recipients = notificationRecipientRepository.findByAnnouncementId(announcement.getId());
		assertThat(recipients).extracting(recipient -> recipient.getPerson().getId()).containsExactly(staffPerson.getId());
	}

	@Test
	void selectedGroupAudiencePublishesToChosenPersonsOnly() {
		Fixture fixture = setUpFixture("comm-selected-group-fanout");
		Person otherPerson = personRepository.save(new Person("Farah", "Khan"));

		Announcement announcement = communicationService.createAnnouncement(fixture.campus(), "Committee Note", "Body",
				AudienceType.SELECTED_GROUP, null, List.of(fixture.guardianPerson(), otherPerson));
		communicationService.publishAnnouncement(announcement);

		List<NotificationRecipient> recipients = notificationRecipientRepository.findByAnnouncementId(announcement.getId());
		assertThat(recipients).extracting(recipient -> recipient.getPerson().getId())
				.containsExactlyInAnyOrder(fixture.guardianPerson().getId(), otherPerson.getId());
	}

	@Test
	void previewAudienceCountsMatchWhatPublishWouldFanOutTo() {
		Fixture fixture = setUpFixture("comm-preview-audience");
		communicationService.setPreference(fixture.guardianPerson(), NotificationChannel.IN_APP, false);

		CommunicationService.AudiencePreview preview = communicationService.previewAudience(
				fixture.campus(), AudienceType.SECTION, fixture.section().getId(), List.of());

		assertThat(preview.audienceSize()).isEqualTo(2);
		assertThat(preview.notifiableCount()).isEqualTo(1);
	}

	@Test
	void previewAudienceDoesNotPersistAnything() {
		Fixture fixture = setUpFixture("comm-preview-no-persist");

		communicationService.previewAudience(fixture.campus(), AudienceType.SECTION, fixture.section().getId(), List.of());

		assertThat(notificationRecipientRepository.findAll()).isEmpty();
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
