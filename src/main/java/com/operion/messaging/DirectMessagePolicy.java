package com.operion.messaging;

import java.util.HashSet;
import java.util.Set;

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
import org.springframework.stereotype.Component;

/**
 * Who may START a new direct thread (SEC-M7). Without this any guardian could open a thread with any person in
 * the organisation, including another child's guardian.
 *
 * <ul>
 * <li>A direct thread between the two people already exists: allowed unchanged, so a thread staff started stays
 * openable by the guardian it was started with.</li>
 * <li>Admin session (PortalSessionPolicy.isPortalOnly is false): allowed for any person, as before.</li>
 * <li>Portal-only session: allowed only along a teacher relationship, judged from the data rather than from the
 * role (a teacher can hold a portal-flagged role): (a) the target has an ACTIVE teacher assignment on a section
 * the requester, or a child linked to the requester through an ACTIVE guardian link, is currently enrolled in; or
 * (b) the requester has an ACTIVE teacher assignment on a section and the target is a currently enrolled student
 * of it or an ACTIVE guardian of one.</li>
 * </ul>
 * Anything else, guardian-to-guardian included, is AuthorizationDeniedException (403).
 */
@Component
public class DirectMessagePolicy {

	private final PortalSessionPolicy portalSessionPolicy;
	private final MessageThreadRepository messageThreadRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final GuardianRepository guardianRepository;
	private final StudentRepository studentRepository;

	public DirectMessagePolicy(PortalSessionPolicy portalSessionPolicy, MessageThreadRepository messageThreadRepository,
			TeacherAssignmentRepository teacherAssignmentRepository, StudentEnrollmentRepository studentEnrollmentRepository,
			StudentGuardianRepository studentGuardianRepository, GuardianRepository guardianRepository,
			StudentRepository studentRepository) {
		this.portalSessionPolicy = portalSessionPolicy;
		this.messageThreadRepository = messageThreadRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.studentGuardianRepository = studentGuardianRepository;
		this.guardianRepository = guardianRepository;
		this.studentRepository = studentRepository;
	}

	/** Throws AuthorizationDeniedException unless {@code requester} (the person behind user {@code actorUserId}) may
	 * open a direct thread with {@code target}. Self-messaging is left to MessagingService, which rejects it. */
	public void requireCanOpen(Long actorUserId, Person requester, Person target) {
		if (messageThreadRepository.findDirectThreadBetween(requester.getId(), target.getId()).isPresent()) {
			return;
		}
		if (!portalSessionPolicy.isPortalOnly(actorUserId)) {
			return;
		}
		if (targetTeachesRequestersSection(requester, target) || requesterTeachesTargetsSection(requester, target)) {
			return;
		}
		throw new AuthorizationDeniedException("You can only start a conversation with a teacher of your child's class, or with someone in your own class");
	}

	/** (a) The target is an active teacher of a section the requester (or the requester's child) is enrolled in. */
	private boolean targetTeachesRequestersSection(Person requester, Person target) {
		Set<Long> targetSections = new HashSet<>();
		for (TeacherAssignment assignment : teacherAssignmentRepository.findByTeacherPersonId(target.getId())) {
			if (assignment.getStatus() == TeacherAssignmentStatus.ACTIVE) {
				targetSections.add(assignment.getSection().getId());
			}
		}
		if (targetSections.isEmpty()) {
			return false;
		}
		for (Student student : studentsOf(requester)) {
			Boolean enrolledInTargetSection = studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId())
					.map(enrollment -> targetSections.contains(enrollment.getSection().getId())).orElse(false);
			if (enrolledInTargetSection) {
				return true;
			}
		}
		return false;
	}

	/** (b) The requester is an active teacher of a section the target is a current student or active guardian of. */
	private boolean requesterTeachesTargetsSection(Person requester, Person target) {
		for (TeacherAssignment assignment : teacherAssignmentRepository.findByTeacherPersonId(requester.getId())) {
			if (assignment.getStatus() != TeacherAssignmentStatus.ACTIVE) {
				continue;
			}
			for (StudentEnrollment enrollment : studentEnrollmentRepository.findBySectionIdAndCurrentTrue(assignment.getSection().getId())) {
				Student student = enrollment.getStudent();
				if (student.getPerson().getId().equals(target.getId())) {
					return true;
				}
				for (StudentGuardian link : studentGuardianRepository.findByStudentId(student.getId())) {
					if (link.getStatus() == StudentGuardianStatus.ACTIVE && link.getGuardian().getPerson().getId().equals(target.getId())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The requester themself when they are a student, plus every child linked to them through an ACTIVE guardian link. */
	private Set<Student> studentsOf(Person person) {
		Set<Student> students = new HashSet<>();
		studentRepository.findByPersonId(person.getId()).ifPresent(students::add);
		guardianRepository.findByPersonId(person.getId()).map(Guardian::getId).ifPresent(guardianId -> {
			for (StudentGuardian link : studentGuardianRepository.findByGuardianId(guardianId)) {
				if (link.getStatus() == StudentGuardianStatus.ACTIVE) {
					students.add(link.getStudent());
				}
			}
		});
		return students;
	}
}
