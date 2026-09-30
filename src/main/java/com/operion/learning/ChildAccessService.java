package com.operion.learning;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRepository;
import com.operion.parent.GuardianStatus;
import com.operion.parent.StudentGuardianRepository;
import com.operion.parent.StudentGuardianStatus;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.stereotype.Service;

/**
 * Same "verify a real StudentGuardian link before touching the child's data" rule as
 * GuardianPortalService, exposed for the learning features a guardian uses on a child's behalf
 * (students have no login of their own).
 */
@Service
public class ChildAccessService {

	/** The guardian's person plus the linked child's current enrollment. */
	public record OwnChild(Long guardianPersonId, StudentEnrollment enrollment) {
	}

	private final OrganisationMembershipRepository membershipRepository;
	private final GuardianRepository guardianRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final StudentEnrollmentRepository enrollmentRepository;

	public ChildAccessService(OrganisationMembershipRepository membershipRepository, GuardianRepository guardianRepository,
			StudentGuardianRepository studentGuardianRepository, StudentEnrollmentRepository enrollmentRepository) {
		this.membershipRepository = membershipRepository;
		this.guardianRepository = guardianRepository;
		this.studentGuardianRepository = studentGuardianRepository;
		this.enrollmentRepository = enrollmentRepository;
	}

	public OwnChild resolve(Long userId, Long studentId) {
		Long personId = membershipRepository.findByUserId(userId).stream()
				.filter(m -> m.getStatus() == MembershipStatus.ACTIVE).findFirst().map(m -> m.getPerson().getId())
				.orElseThrow(() -> new AuthorizationDeniedException("No active membership for this user"));
		Guardian guardian = guardianRepository.findByPersonId(personId)
				.filter(g -> g.getStatus() == GuardianStatus.ACTIVE)
				.orElseThrow(() -> new AuthorizationDeniedException("Caller is not an active guardian"));
		boolean linked = studentGuardianRepository.findByGuardianId(guardian.getId()).stream()
				.anyMatch(sg -> sg.getStatus() == StudentGuardianStatus.ACTIVE && sg.getStudent().getId().equals(studentId));
		if (!linked) {
			throw new AuthorizationDeniedException("Caller is not linked to student " + studentId);
		}
		StudentEnrollment enrollment = enrollmentRepository.findByStudentIdAndCurrentTrue(studentId)
				.orElseThrow(() -> new IllegalArgumentException("No current enrollment for student " + studentId));
		return new OwnChild(personId, enrollment);
	}
}
