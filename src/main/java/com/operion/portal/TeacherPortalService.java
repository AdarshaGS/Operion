package com.operion.portal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.hr.HrService;
import com.operion.hr.LeaveBalanceRepository;
import com.operion.hr.LeaveRequest;
import com.operion.hr.LeaveRequestRepository;
import com.operion.hr.LeaveType;
import com.operion.hr.LeaveTypeRepository;
import com.operion.hr.LeaveTypeStatus;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.stereotype.Service;

/**
 * Every method is scoped to the caller's own StaffProfile/TeacherAssignments - a portal
 * role carries none of HR_VIEW/HR_LEAVE_MANAGE, the admin permissions that gate the
 * equivalent HR-facing endpoints, see #208.
 */
@Service
public class TeacherPortalService {

	private final OrganisationMembershipRepository membershipRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final HrService hrService;
	private final LeaveRequestRepository leaveRequestRepository;
	private final LeaveBalanceRepository leaveBalanceRepository;
	private final LeaveTypeRepository leaveTypeRepository;
	private final AcademicYearRepository academicYearRepository;

	public TeacherPortalService(OrganisationMembershipRepository membershipRepository,
			TeacherAssignmentRepository teacherAssignmentRepository, StaffProfileRepository staffProfileRepository, HrService hrService,
			LeaveRequestRepository leaveRequestRepository, LeaveBalanceRepository leaveBalanceRepository,
			LeaveTypeRepository leaveTypeRepository, AcademicYearRepository academicYearRepository) {
		this.membershipRepository = membershipRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.hrService = hrService;
		this.leaveRequestRepository = leaveRequestRepository;
		this.leaveBalanceRepository = leaveBalanceRepository;
		this.leaveTypeRepository = leaveTypeRepository;
		this.academicYearRepository = academicYearRepository;
	}

	/** Catalog data (leave type names), not staff-specific - safe to expose to any
	 * authenticated portal session without an HR_VIEW check, unlike LeaveTypeController's
	 * admin-facing GET. */
	public List<LeaveType> leaveTypeCatalog() {
		return leaveTypeRepository.findByStatus(LeaveTypeStatus.ACTIVE);
	}

	public List<TeacherSectionSummary> sectionsFor(Long userId) {
		Long personId = requireActivePersonId(userId);
		return teacherAssignmentRepository.findByTeacherPersonId(personId).stream()
				.filter(assignment -> assignment.getStatus() == TeacherAssignmentStatus.ACTIVE)
				.map(TeacherSectionSummary::from)
				.toList();
	}

	public List<TeacherLeaveBalanceSummary> leaveBalanceFor(Long userId, Long academicYearId) {
		StaffProfile staffProfile = requireOwnStaffProfile(userId);
		AcademicYear academicYear = findAcademicYear(academicYearId);
		return leaveTypeRepository.findAll().stream()
				.map(leaveType -> {
					double allocated = leaveBalanceRepository
							.findByStaffProfileIdAndLeaveTypeIdAndAcademicYearId(staffProfile.getId(), leaveType.getId(), academicYear.getId())
							.map(balance -> balance.getAllocatedDays())
							.orElse(0.0);
					double remaining = hrService.getRemainingBalance(staffProfile, leaveType, academicYear);
					return new TeacherLeaveBalanceSummary(leaveType.getId(), leaveType.getName(), allocated, remaining);
				})
				.toList();
	}

	public List<TeacherLeaveRequestSummary> leaveRequestsFor(Long userId) {
		StaffProfile staffProfile = requireOwnStaffProfile(userId);
		return leaveRequestRepository.findByStaffProfileId(staffProfile.getId()).stream().map(TeacherLeaveRequestSummary::from).toList();
	}

	public TeacherLeaveRequestSummary raiseLeaveRequest(Long userId, Long leaveTypeId, Long academicYearId, LocalDate startDate,
			LocalDate endDate, double numberOfDays, String reason) {
		StaffProfile staffProfile = requireOwnStaffProfile(userId);
		LeaveType leaveType = leaveTypeRepository.findById(leaveTypeId)
				.orElseThrow(() -> new IllegalArgumentException("No leave type with id " + leaveTypeId));
		AcademicYear academicYear = findAcademicYear(academicYearId);
		LeaveRequest leaveRequest = hrService.raiseLeaveRequest(staffProfile, leaveType, academicYear, startDate, endDate, numberOfDays, reason);
		return TeacherLeaveRequestSummary.from(leaveRequest);
	}

	private AcademicYear findAcademicYear(Long id) {
		return academicYearRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No academic year with id " + id));
	}

	private StaffProfile requireOwnStaffProfile(Long userId) {
		Long personId = requireActivePersonId(userId);
		return staffProfileRepository.findByPersonId(personId)
				.orElseThrow(() -> new AuthorizationDeniedException("Caller has no staff profile"));
	}

	private Long requireActivePersonId(Long userId) {
		return activeMembership(userId).map(m -> m.getPerson().getId())
				.orElseThrow(() -> new AuthorizationDeniedException("No active membership for this user"));
	}

	private Optional<OrganisationMembership> activeMembership(Long userId) {
		return membershipRepository.findByUserId(userId).stream().filter(m -> m.getStatus() == MembershipStatus.ACTIVE).findFirst();
	}
}
