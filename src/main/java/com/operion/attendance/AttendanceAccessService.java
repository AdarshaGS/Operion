package com.operion.attendance;

import java.util.List;
import java.util.Set;

import com.operion.academic.Section;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.academic.TeacherAssignmentType;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.PermissionChecker;
import org.springframework.stereotype.Service;

/**
 * Which sections a caller may mark daily attendance for. ATTENDANCE_MARK_ANY_SECTION
 * (or Owner / ALL_FUNCTIONS) covers every section; plain ATTENDANCE_MARK covers only
 * the sections the caller is the ACTIVE homeroom (class) teacher of.
 */
@Service
public class AttendanceAccessService {

	public static final String MARK = "ATTENDANCE_MARK";
	public static final String MARK_ANY_SECTION = "ATTENDANCE_MARK_ANY_SECTION";

	public enum MarkScope { ALL, OWN, NONE }

	/** schoolClassIds: the classes the OWN sections belong to, so a picker can narrow both dropdowns. */
	public record MarkableSections(MarkScope scope, List<Long> sectionIds, List<Long> schoolClassIds) {
	}

	private final PermissionChecker permissionChecker;
	private final OrganisationMembershipRepository membershipRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;

	public AttendanceAccessService(PermissionChecker permissionChecker,
			OrganisationMembershipRepository membershipRepository,
			TeacherAssignmentRepository teacherAssignmentRepository) {
		this.permissionChecker = permissionChecker;
		this.membershipRepository = membershipRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
	}

	public MarkableSections markableSections(Long userId) {
		if (permissionChecker.holdsAny(userId, Set.of(MARK_ANY_SECTION))) {
			return new MarkableSections(MarkScope.ALL, List.of(), List.of());
		}
		if (!permissionChecker.holdsAny(userId, Set.of(MARK))) {
			return new MarkableSections(MarkScope.NONE, List.of(), List.of());
		}
		List<Section> sections = homeroomSections(userId);
		return new MarkableSections(MarkScope.OWN,
				sections.stream().map(Section::getId).toList(),
				sections.stream().map(s -> s.getSchoolClass().getId()).distinct().toList());
	}

	public void requireCanMark(Long userId, Section section) {
		MarkableSections markable = markableSections(userId);
		if (markable.scope() == MarkScope.ALL) {
			return;
		}
		if (markable.scope() == MarkScope.NONE || !markable.sectionIds().contains(section.getId())) {
			throw new AuthorizationDeniedException(
					"Only the class teacher of this section (or a holder of " + MARK_ANY_SECTION + ") can mark its attendance");
		}
	}

	private List<Section> homeroomSections(Long userId) {
		return membershipRepository.findByUserId(userId).stream()
				.filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
				.map(m -> m.getPerson().getId())
				.distinct()
				.flatMap(personId -> teacherAssignmentRepository.findByTeacherPersonId(personId).stream())
				.filter(a -> a.getStatus() == TeacherAssignmentStatus.ACTIVE
						&& a.getAssignmentType() == TeacherAssignmentType.HOMEROOM)
				.map(a -> a.getSection())
				.distinct()
				.toList();
	}
}
