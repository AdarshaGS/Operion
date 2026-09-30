package com.operion.learning;

import java.util.List;
import java.util.Set;

import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import org.springframework.stereotype.Service;

/** Resolves the calling user into a {@link LearningActor} - admin access levels plus own teaching assignments. */
@Service
public class LearningAccessService {

	private final OrganisationMembershipRepository membershipRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;

	public LearningAccessService(OrganisationMembershipRepository membershipRepository,
			TeacherAssignmentRepository teacherAssignmentRepository) {
		this.membershipRepository = membershipRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
	}

	public LearningActor resolve(Long userId) {
		if (userId == null) {
			throw new AuthorizationDeniedException("No authenticated caller");
		}
		OrganisationMembership membership = membershipRepository.findByUserId(userId).stream()
				.filter(m -> m.getStatus() == MembershipStatus.ACTIVE).findFirst()
				.orElseThrow(() -> new AuthorizationDeniedException("No active membership for this user"));
		Long personId = membership.getPerson().getId();

		Set<String> codes = membershipRepository.findActivePermissionCodesForUser(userId);
		boolean owner = membershipRepository.existsByUserIdAndStatusAndOwner(userId, MembershipStatus.ACTIVE, true);
		boolean all = owner || codes.contains("ALL_FUNCTIONS");
		boolean manager = all || codes.contains("LEARNING_MANAGE");
		boolean viewer = manager || codes.contains("LEARNING_VIEW");

		List<LearningActor.Teaching> teaching = teacherAssignmentRepository.findByTeacherPersonId(personId).stream()
				.filter(a -> a.getStatus() == TeacherAssignmentStatus.ACTIVE)
				.map(this::toTeaching)
				.toList();

		if (!viewer && teaching.isEmpty()) {
			throw new AuthorizationDeniedException("You have no learning access: not a teacher and missing LEARNING_VIEW");
		}
		return new LearningActor(userId, personId, manager, viewer, teaching);
	}

	private LearningActor.Teaching toTeaching(TeacherAssignment assignment) {
		return new LearningActor.Teaching(assignment.getSection().getId(),
				assignment.getSubject() == null ? null : assignment.getSubject().getId());
	}
}
