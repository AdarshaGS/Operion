package com.operion.portal;

import java.util.ArrayList;
import java.util.List;

import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRepository;
import com.operion.parent.GuardianStatus;
import com.operion.parent.StudentGuardianRepository;
import com.operion.parent.StudentGuardianStatus;
import com.operion.student.Student;
import org.springframework.stereotype.Service;

/**
 * Answers "does this login land in the admin shell or a self-service portal shell" -
 * see {@code Role#isPortal()} for why this is a role-level flag rather than inferred
 * from permissions or a hardcoded role name.
 */
@Service
public class PortalIdentityService {

	private final OrganisationMembershipRepository membershipRepository;
	private final GuardianRepository guardianRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;

	public PortalIdentityService(OrganisationMembershipRepository membershipRepository, GuardianRepository guardianRepository,
			StudentGuardianRepository studentGuardianRepository, TeacherAssignmentRepository teacherAssignmentRepository) {
		this.membershipRepository = membershipRepository;
		this.guardianRepository = guardianRepository;
		this.studentGuardianRepository = studentGuardianRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
	}

	public MeResponse resolve(Long userId) {
		List<OrganisationMembership> activeMemberships = membershipRepository.findByUserId(userId).stream()
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.toList();

		// No active membership, an Owner, or any non-portal role present - land in the
		// admin shell exactly as today. Owner always wins regardless of its role's flag,
		// so a portal-flagged role can never accidentally lock out the org's own owner.
		boolean isAdminSession = activeMemberships.isEmpty()
				|| activeMemberships.stream().anyMatch(membership -> membership.isOwner() || !membership.getRole().isPortal());
		if (isAdminSession) {
			return new MeResponse("ADMIN", List.of());
		}

		// One user maps to one Person within a given org (multiple roles are multiple
		// membership rows sharing that same person), same assumption /auth/me makes.
		Long personId = activeMemberships.get(0).getPerson().getId();
		List<PortalContext> contexts = new ArrayList<>();

		guardianRepository.findByPersonId(personId)
				.filter(guardian -> guardian.getStatus() == GuardianStatus.ACTIVE)
				.ifPresent(guardian -> contexts.add(guardianContext(guardian)));

		boolean isTeacher = teacherAssignmentRepository.findByTeacherPersonId(personId).stream()
				.anyMatch(assignment -> assignment.getStatus() == TeacherAssignmentStatus.ACTIVE);
		if (isTeacher) {
			contexts.add(new PortalContext(PortalContextType.TEACHER, null, null, personId));
		}

		return new MeResponse("PORTAL", contexts);
	}

	private PortalContext guardianContext(Guardian guardian) {
		List<ChildSummary> children = studentGuardianRepository.findByGuardianId(guardian.getId()).stream()
				.filter(studentGuardian -> studentGuardian.getStatus() == StudentGuardianStatus.ACTIVE)
				.map(studentGuardian -> {
					Student student = studentGuardian.getStudent();
					String name = student.getPerson().getFirstName() + " " + student.getPerson().getLastName();
					return new ChildSummary(student.getId(), student.getPerson().getId(), name);
				})
				.toList();
		return new PortalContext(PortalContextType.GUARDIAN, guardian.getId(), children, null);
	}
}
