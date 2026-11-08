package com.operion.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.operion.academic.Section;
import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.PortalSessionPolicy;
import com.operion.identity.Person;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRepository;
import com.operion.parent.StudentGuardian;
import com.operion.parent.StudentGuardianRepository;
import com.operion.parent.StudentGuardianStatus;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SEC-M7: a portal-only user can start a direct thread only along a teacher relationship. */
class DirectMessagePolicyTest {

	private static final long ACTOR = 7L;

	private final PortalSessionPolicy portalSessionPolicy = mock(PortalSessionPolicy.class);
	private final MessageThreadRepository messageThreadRepository = mock(MessageThreadRepository.class);
	private final TeacherAssignmentRepository teacherAssignmentRepository = mock(TeacherAssignmentRepository.class);
	private final StudentEnrollmentRepository studentEnrollmentRepository = mock(StudentEnrollmentRepository.class);
	private final StudentGuardianRepository studentGuardianRepository = mock(StudentGuardianRepository.class);
	private final GuardianRepository guardianRepository = mock(GuardianRepository.class);
	private final StudentRepository studentRepository = mock(StudentRepository.class);
	private final DirectMessagePolicy policy = new DirectMessagePolicy(portalSessionPolicy, messageThreadRepository,
			teacherAssignmentRepository, studentEnrollmentRepository, studentGuardianRepository, guardianRepository,
			studentRepository);

	// Section 10: guardian G1's child (student S1) is enrolled, taught by T1, who also has a second guardian G2 in S2's family.
	// Section 20: T2 teaches; student S2 (child of G2) is enrolled there.
	private final Person staff = person(1L);
	private final Person guardian1 = person(2L);
	private final Person guardian2 = person(3L);
	private final Person teacher1 = person(4L);
	private final Person teacher2 = person(5L);

	@BeforeEach
	void setUp() {
		when(messageThreadRepository.findDirectThreadBetween(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
				.thenReturn(Optional.empty());
		when(portalSessionPolicy.isPortalOnly(ACTOR)).thenReturn(true);

		Section section10 = section(10L);
		Section section20 = section(20L);
		Student student1 = student(100L, person(11L));
		Student student2 = student(200L, person(12L));

		wireGuardian(guardian1, 50L, student1);
		wireGuardian(guardian2, 60L, student2);

		StudentEnrollment enrollment1 = enrollment(student1, section10);
		StudentEnrollment enrollment2 = enrollment(student2, section20);
		when(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(100L)).thenReturn(Optional.of(enrollment1));
		when(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(200L)).thenReturn(Optional.of(enrollment2));
		when(studentEnrollmentRepository.findBySectionIdAndCurrentTrue(10L)).thenReturn(List.of(enrollment1));
		when(studentEnrollmentRepository.findBySectionIdAndCurrentTrue(20L)).thenReturn(List.of(enrollment2));

		TeacherAssignment assignment1 = assignment(section10, TeacherAssignmentStatus.ACTIVE);
		TeacherAssignment assignment2 = assignment(section20, TeacherAssignmentStatus.ACTIVE);
		when(teacherAssignmentRepository.findByTeacherPersonId(4L)).thenReturn(List.of(assignment1));
		when(teacherAssignmentRepository.findByTeacherPersonId(5L)).thenReturn(List.of(assignment2));
	}

	@Test
	void staffCanStartAThreadWithAGuardian() {
		when(portalSessionPolicy.isPortalOnly(ACTOR)).thenReturn(false);

		assertThatCode(() -> policy.requireCanOpen(ACTOR, staff, guardian1)).doesNotThrowAnyException();
	}

	@Test
	void aGuardianCanMessageATeacherOfTheirChildsSection() {
		assertThatCode(() -> policy.requireCanOpen(ACTOR, guardian1, teacher1)).doesNotThrowAnyException();
	}

	@Test
	void aGuardianCannotMessageATeacherOfAnotherSection() {
		assertThatThrownBy(() -> policy.requireCanOpen(ACTOR, guardian1, teacher2)).isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void aGuardianCannotMessageAnotherGuardian() {
		assertThatThrownBy(() -> policy.requireCanOpen(ACTOR, guardian1, guardian2)).isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void aGuardianCannotMessageAStaffMemberWithNoTeachingLink() {
		assertThatThrownBy(() -> policy.requireCanOpen(ACTOR, guardian1, staff)).isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void aTeachersEndedAssignmentGivesNoAccess() {
		Section section10 = section(10L);
		TeacherAssignment ended = assignment(section10, TeacherAssignmentStatus.ENDED);
		when(teacherAssignmentRepository.findByTeacherPersonId(4L)).thenReturn(List.of(ended));

		assertThatThrownBy(() -> policy.requireCanOpen(ACTOR, guardian1, teacher1)).isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void aGuardianCanReopenAnExistingStaffStartedThread() {
		MessageThread existing = mock(MessageThread.class);
		when(messageThreadRepository.findDirectThreadBetween(2L, 1L)).thenReturn(Optional.of(existing));

		assertThatCode(() -> policy.requireCanOpen(ACTOR, guardian1, staff)).doesNotThrowAnyException();
	}

	@Test
	void aPortalFlaggedTeacherCanMessageAGuardianInTheirSection() {
		assertThatCode(() -> policy.requireCanOpen(ACTOR, teacher1, guardian1)).doesNotThrowAnyException();
	}

	@Test
	void aPortalFlaggedTeacherCannotMessageAGuardianOutsideTheirSections() {
		assertThatThrownBy(() -> policy.requireCanOpen(ACTOR, teacher1, guardian2)).isInstanceOf(AuthorizationDeniedException.class);
	}

	private static Person person(Long id) {
		Person person = mock(Person.class);
		when(person.getId()).thenReturn(id);
		return person;
	}

	private static Section section(Long id) {
		Section section = mock(Section.class);
		when(section.getId()).thenReturn(id);
		return section;
	}

	private static Student student(Long id, Person person) {
		Student student = mock(Student.class);
		when(student.getId()).thenReturn(id);
		when(student.getPerson()).thenReturn(person);
		return student;
	}

	private static StudentEnrollment enrollment(Student student, Section section) {
		StudentEnrollment enrollment = mock(StudentEnrollment.class);
		when(enrollment.getStudent()).thenReturn(student);
		when(enrollment.getSection()).thenReturn(section);
		return enrollment;
	}

	private static TeacherAssignment assignment(Section section, TeacherAssignmentStatus status) {
		TeacherAssignment assignment = mock(TeacherAssignment.class);
		when(assignment.getSection()).thenReturn(section);
		when(assignment.getStatus()).thenReturn(status);
		return assignment;
	}

	private void wireGuardian(Person guardianPerson, Long guardianId, Student child) {
		Guardian guardian = mock(Guardian.class);
		when(guardian.getId()).thenReturn(guardianId);
		when(guardian.getPerson()).thenReturn(guardianPerson);
		StudentGuardian link = mock(StudentGuardian.class);
		when(link.getStatus()).thenReturn(StudentGuardianStatus.ACTIVE);
		when(link.getGuardian()).thenReturn(guardian);
		when(link.getStudent()).thenReturn(child);
		when(guardianRepository.findByPersonId(guardianPerson.getId())).thenReturn(Optional.of(guardian));
		when(studentGuardianRepository.findByGuardianId(guardianId)).thenReturn(List.of(link));
		when(studentGuardianRepository.findByStudentId(child.getId())).thenReturn(List.of(link));
	}
}
