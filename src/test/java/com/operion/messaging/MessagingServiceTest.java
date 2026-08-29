package com.operion.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentType;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.messaging.api.MessageResponse;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves class-group auto-membership (student + guardian + active teacher, matching
 * MessagingService's own doc on the CommunicationService gap it closes), thread
 * get-or-create idempotency both ways, participant-only read/send, and that sending
 * broadcasts and marks the sender's own read position current.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, ParentService.class, MessagingService.class,
		MessagingServiceTest.RecordingBroadcasterConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MessagingServiceTest {

	@Autowired
	private MessagingService messagingService;

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
	private TeacherAssignmentRepository teacherAssignmentRepository;

	@Autowired
	private RecordingBroadcaster broadcaster;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
		broadcaster.received.clear();
	}

	private record Fixture(Section section, Student student, Person guardianPerson, Person teacherPerson) {
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
				new Student(studentPerson, "ADM-200", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null));
		studentEnrollmentRepository.save(new StudentEnrollment(student, year, section, 1, LocalDate.of(2025, 6, 1)));

		Person guardianPerson = personRepository.save(new Person("Vikram", "Shah"));
		Guardian guardian = parentService.getOrCreateGuardian(guardianPerson, "Engineer");
		parentService.linkGuardian(student, guardian, GuardianRelationshipType.FATHER, true, true, true, true, 1);

		Person teacherPerson = personRepository.save(new Person("Meera", "Nair"));
		teacherAssignmentRepository.save(
				new TeacherAssignment(year, section, null, teacherPerson, TeacherAssignmentType.HOMEROOM, LocalDate.of(2025, 6, 1)));

		return new Fixture(section, student, guardianPerson, teacherPerson);
	}

	@Test
	void classGroupThreadIncludesStudentGuardianAndActiveTeacher() {
		Fixture fixture = setUpFixture("msg-class-group");

		MessageThread thread = messagingService.getOrCreateClassGroupThread(fixture.section());

		List<Long> participantPersonIds = messagingService.listParticipants(thread).stream().map(p -> p.getPerson().getId()).toList();
		assertThat(participantPersonIds).containsExactlyInAnyOrder(
				fixture.student().getPerson().getId(), fixture.guardianPerson().getId(), fixture.teacherPerson().getId());
	}

	@Test
	void classGroupThreadIsGetOrCreateIdempotent() {
		Fixture fixture = setUpFixture("msg-class-group-idempotent");

		MessageThread first = messagingService.getOrCreateClassGroupThread(fixture.section());
		MessageThread second = messagingService.getOrCreateClassGroupThread(fixture.section());

		assertThat(second.getId()).isEqualTo(first.getId());
	}

	@Test
	void directThreadIsIdempotentRegardlessOfArgumentOrder() {
		setUpFixture("msg-direct-idempotent");
		Person personA = personRepository.save(new Person("Asha", "Rao"));
		Person personB = personRepository.save(new Person("Rohit", "Nair"));

		MessageThread first = messagingService.getOrCreateDirectThread(personA, personB);
		MessageThread second = messagingService.getOrCreateDirectThread(personB, personA);

		assertThat(second.getId()).isEqualTo(first.getId());
	}

	@Test
	void cannotOpenADirectThreadWithYourself() {
		setUpFixture("msg-direct-self");
		Person person = personRepository.save(new Person("Asha", "Rao"));

		assertThatThrownBy(() -> messagingService.getOrCreateDirectThread(person, person)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void sendingRequiresParticipation() {
		setUpFixture("msg-send-not-participant");
		Person personA = personRepository.save(new Person("Asha", "Rao"));
		Person personB = personRepository.save(new Person("Rohit", "Nair"));
		Person outsider = personRepository.save(new Person("Farah", "Khan"));
		MessageThread thread = messagingService.getOrCreateDirectThread(personA, personB);

		assertThatThrownBy(() -> messagingService.sendMessage(thread, outsider, "hi"))
				.isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void sendingBroadcastsAndMarksTheSenderRead() {
		setUpFixture("msg-send-broadcast");
		Person personA = personRepository.save(new Person("Asha", "Rao"));
		Person personB = personRepository.save(new Person("Rohit", "Nair"));
		MessageThread thread = messagingService.getOrCreateDirectThread(personA, personB);

		messagingService.sendMessage(thread, personA, "Hello there");

		assertThat(broadcaster.received).hasSize(1);
		assertThat(broadcaster.received.get(0).body()).isEqualTo("Hello there");
		assertThat(messagingService.isUnreadFor(thread, personA)).isFalse();
		assertThat(messagingService.isUnreadFor(thread, personB)).isTrue();
	}

	@Test
	void markReadClearsTheUnreadFlag() {
		setUpFixture("msg-mark-read");
		Person personA = personRepository.save(new Person("Asha", "Rao"));
		Person personB = personRepository.save(new Person("Rohit", "Nair"));
		MessageThread thread = messagingService.getOrCreateDirectThread(personA, personB);
		messagingService.sendMessage(thread, personA, "Hello there");

		messagingService.markRead(thread, personB);

		assertThat(messagingService.isUnreadFor(thread, personB)).isFalse();
	}

	@TestConfiguration
	static class RecordingBroadcasterConfig {

		@Bean
		RecordingBroadcaster messageBroadcaster() {
			return new RecordingBroadcaster();
		}
	}

	static class RecordingBroadcaster implements MessageBroadcaster {
		final List<MessageResponse> received = new ArrayList<>();

		@Override
		public void broadcast(Long threadId, MessageResponse message) {
			received.add(message);
		}
	}
}
