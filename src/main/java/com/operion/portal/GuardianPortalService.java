package com.operion.portal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.operion.attendance.AttendanceService;
import com.operion.attendance.StudentAttendance;
import com.operion.attendance.StudentAttendanceRepository;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.communication.Announcement;
import com.operion.communication.AnnouncementRepository;
import com.operion.communication.AnnouncementStatus;
import com.operion.communication.AudienceType;
import com.operion.examination.ReportCardRepository;
import com.operion.examination.ReportCardStatus;
import com.operion.finance.InvoiceRepository;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRepository;
import com.operion.parent.GuardianStatus;
import com.operion.parent.StudentGuardianRepository;
import com.operion.parent.StudentGuardianStatus;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.stereotype.Service;

/**
 * Every method here is scoped to the caller's own linked children (#207) - verified via
 * Guardian + StudentGuardian before touching a single attendance/fee/exam record, since
 * a portal role typically carries none of the admin STUDENT_VIEW-family permissions that
 * would otherwise gate this data.
 */
@Service
public class GuardianPortalService {

	private final OrganisationMembershipRepository membershipRepository;
	private final GuardianRepository guardianRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final AttendanceService attendanceService;
	private final StudentAttendanceRepository studentAttendanceRepository;
	private final InvoiceRepository invoiceRepository;
	private final ReportCardRepository reportCardRepository;
	private final AnnouncementRepository announcementRepository;

	public GuardianPortalService(OrganisationMembershipRepository membershipRepository, GuardianRepository guardianRepository,
			StudentGuardianRepository studentGuardianRepository, StudentEnrollmentRepository studentEnrollmentRepository,
			AttendanceService attendanceService, StudentAttendanceRepository studentAttendanceRepository,
			InvoiceRepository invoiceRepository, ReportCardRepository reportCardRepository,
			AnnouncementRepository announcementRepository) {
		this.membershipRepository = membershipRepository;
		this.guardianRepository = guardianRepository;
		this.studentGuardianRepository = studentGuardianRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.attendanceService = attendanceService;
		this.studentAttendanceRepository = studentAttendanceRepository;
		this.invoiceRepository = invoiceRepository;
		this.reportCardRepository = reportCardRepository;
		this.announcementRepository = announcementRepository;
	}

	public GuardianAttendanceSummary attendanceFor(Long userId, Long studentId) {
		StudentEnrollment enrollment = currentEnrollmentForOwnChild(userId, studentId);
		LocalDate today = LocalDate.now();
		var todayStatus = studentAttendanceRepository.findByStudentEnrollmentIdAndAttendanceDate(enrollment.getId(), today)
				.map(StudentAttendance::getAttendanceStatus)
				.orElse(null);
		var monthly = attendanceService.monthlySummary(enrollment.getId(), today.getYear(), today.getMonthValue());
		return new GuardianAttendanceSummary(todayStatus, monthly);
	}

	public List<GuardianInvoiceSummary> feesFor(Long userId, Long studentId) {
		StudentEnrollment enrollment = currentEnrollmentForOwnChild(userId, studentId);
		return invoiceRepository.findByStudentFeeAssignment_StudentEnrollmentId(enrollment.getId()).stream()
				.map(GuardianInvoiceSummary::from)
				.toList();
	}

	public List<GuardianReportCardSummary> reportCardsFor(Long userId, Long studentId) {
		StudentEnrollment enrollment = currentEnrollmentForOwnChild(userId, studentId);
		return reportCardRepository.findByStudentEnrollmentId(enrollment.getId()).stream()
				.filter(reportCard -> reportCard.getStatus() == ReportCardStatus.PUBLISHED && !reportCard.isStale())
				.map(GuardianReportCardSummary::from)
				.toList();
	}

	/** Org-wide and (if the caller's own membership has one) same-campus announcements -
	 * class/section-targeted and staff-only announcements are out of scope for this pass,
	 * see #207's design note. */
	public List<PortalAnnouncementSummary> announcementsFor(Long userId) {
		Long campusId = activeMembership(userId).map(m -> m.getCampus()).map(campus -> campus.getId()).orElse(null);
		return announcementRepository.findByStatus(AnnouncementStatus.PUBLISHED).stream()
				.filter(announcement -> announcement.getAudienceType() == AudienceType.ORG
						|| (announcement.getAudienceType() == AudienceType.CAMPUS && announcement.getCampus() != null
								&& announcement.getCampus().getId().equals(campusId)))
				.sorted((a, b) -> compareByPublishedAtDesc(a, b))
				.map(PortalAnnouncementSummary::from)
				.toList();
	}

	private int compareByPublishedAtDesc(Announcement a, Announcement b) {
		if (a.getPublishedAt() == null || b.getPublishedAt() == null) {
			return 0;
		}
		return b.getPublishedAt().compareTo(a.getPublishedAt());
	}

	private StudentEnrollment currentEnrollmentForOwnChild(Long userId, Long studentId) {
		Long personId = requireActivePersonId(userId);
		Guardian guardian = guardianRepository.findByPersonId(personId)
				.filter(g -> g.getStatus() == GuardianStatus.ACTIVE)
				.orElseThrow(() -> new AuthorizationDeniedException("Caller is not an active guardian"));
		boolean linked = studentGuardianRepository.findByGuardianId(guardian.getId()).stream()
				.anyMatch(sg -> sg.getStatus() == StudentGuardianStatus.ACTIVE && sg.getStudent().getId().equals(studentId));
		if (!linked) {
			throw new AuthorizationDeniedException("Caller is not linked to student " + studentId);
		}
		return studentEnrollmentRepository.findByStudentIdAndCurrentTrue(studentId)
				.orElseThrow(() -> new IllegalArgumentException("No current enrollment for student " + studentId));
	}

	private Long requireActivePersonId(Long userId) {
		return activeMembership(userId).map(m -> m.getPerson().getId())
				.orElseThrow(() -> new AuthorizationDeniedException("No active membership for this user"));
	}

	private Optional<OrganisationMembership> activeMembership(Long userId) {
		return membershipRepository.findByUserId(userId).stream()
				.filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
				.findFirst();
	}
}
