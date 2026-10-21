package com.operion.hr;

import com.operion.common.InvalidRequestException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.StaffAttendance;
import com.operion.attendance.StaffAttendanceRepository;
import com.operion.audit.AuditLogService;
import com.operion.calendar.WorkingDayService;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.Campus;
import com.operion.organisation.Department;
import com.operion.organisation.Designation;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns staff profiles, leave allocation/approval, and staff documents. Leave balance
 * is a stored entitlement (LeaveBalance.allocatedDays) but "used" days are computed
 * live by summing APPROVED LeaveRequests, mirroring Inventory's live-balance pattern -
 * approving a request that would exceed the remaining balance is rejected, and a
 * missing LeaveBalance row means no entitlement (same "explicit row required"
 * philosophy as invoice-before-payment in Fees).
 */
@Service
public class HrService {

	private final StaffProfileRepository staffProfileRepository;
	private final LeaveTypeRepository leaveTypeRepository;
	private final LeaveBalanceRepository leaveBalanceRepository;
	private final LeaveRequestRepository leaveRequestRepository;
	private final StaffDocumentRepository staffDocumentRepository;
	private final JobApplicationRepository jobApplicationRepository;
	private final OrganisationRepository organisationRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final StaffExitRepository staffExitRepository;
	private final StaffBankDetailRepository staffBankDetailRepository;
	private final StaffAttendanceRepository staffAttendanceRepository;
	private final WorkingDayService workingDayService;
	private final AuditLogService auditLogService;

	public HrService(StaffProfileRepository staffProfileRepository, LeaveTypeRepository leaveTypeRepository,
			LeaveBalanceRepository leaveBalanceRepository, LeaveRequestRepository leaveRequestRepository,
			StaffDocumentRepository staffDocumentRepository, JobApplicationRepository jobApplicationRepository,
			OrganisationRepository organisationRepository, StaffAssignmentRepository staffAssignmentRepository,
			StaffExitRepository staffExitRepository, StaffBankDetailRepository staffBankDetailRepository,
			StaffAttendanceRepository staffAttendanceRepository, WorkingDayService workingDayService,
			AuditLogService auditLogService) {
		this.staffProfileRepository = staffProfileRepository;
		this.leaveTypeRepository = leaveTypeRepository;
		this.leaveBalanceRepository = leaveBalanceRepository;
		this.leaveRequestRepository = leaveRequestRepository;
		this.staffDocumentRepository = staffDocumentRepository;
		this.jobApplicationRepository = jobApplicationRepository;
		this.organisationRepository = organisationRepository;
		this.staffAssignmentRepository = staffAssignmentRepository;
		this.staffExitRepository = staffExitRepository;
		this.staffBankDetailRepository = staffBankDetailRepository;
		this.staffAttendanceRepository = staffAttendanceRepository;
		this.workingDayService = workingDayService;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public StaffProfile createStaffProfile(Person person, Campus campus, String employeeCode, Designation designation,
			Department department, LocalDate dateOfJoining, EmploymentType employmentType) {
		if (staffProfileRepository.findByPersonId(person.getId()).isPresent()) {
			throw new IllegalStateException("This person already has a staff profile");
		}
		StaffProfile staffProfile = staffProfileRepository.save(
				new StaffProfile(person, campus, employeeCode, designation, department, dateOfJoining, employmentType));
		staffAssignmentRepository.save(new StaffAssignment(staffProfile, campus, department, designation, dateOfJoining));
		return staffProfile;
	}

	public StaffProfile changeStaffStatus(StaffProfile staffProfile, StaffProfileStatus status) {
		StaffProfileStatus previous = staffProfile.getStatus();
		staffProfile.changeStatus(status);
		StaffProfile saved = staffProfileRepository.save(staffProfile);
		auditLogService.record("StaffProfile", staffProfile.getId(), "STATUS_CHANGE", previous, status);
		return saved;
	}

	/** Ends the current StaffAssignment and opens a new one, mirroring TeacherAssignment's
	 * insert-only reassignment - null campus/department is a valid target (org-wide staff). */
	@Transactional
	public StaffProfile transfer(StaffProfile staffProfile, Campus campus, Department department, Designation designation,
			LocalDate effectiveDate) {
		Map<String, Object> before = Map.of(
				"campusId", staffProfile.getCampus() == null ? "" : staffProfile.getCampus().getId(),
				"departmentId", staffProfile.getDepartment() == null ? "" : staffProfile.getDepartment().getId(),
				"designationId", staffProfile.getDesignation().getId());

		staffAssignmentRepository.findByStaffProfileIdAndStatus(staffProfile.getId(), StaffAssignmentStatus.ACTIVE)
				.ifPresent(current -> {
					current.end(effectiveDate);
					staffAssignmentRepository.save(current);
				});
		staffAssignmentRepository.save(new StaffAssignment(staffProfile, campus, department, designation, effectiveDate));

		staffProfile.transfer(campus, department, designation);
		StaffProfile saved = staffProfileRepository.save(staffProfile);

		Map<String, Object> after = Map.of(
				"campusId", campus == null ? "" : campus.getId(),
				"departmentId", department == null ? "" : department.getId(),
				"designationId", designation.getId());
		auditLogService.record("StaffProfile", staffProfile.getId(), "TRANSFER", before, after);
		return saved;
	}

	/** Also flips the master status - RESIGNATION/RETIREMENT/CONTRACT_END map to RESIGNED,
	 * TERMINATION to TERMINATED, same "exit call also updates status" shape as
	 * StudentService.recordExit. */
	@Transactional
	public StaffExit recordExit(StaffProfile staffProfile, StaffExitType exitType, LocalDate exitDate, String reason, Long initiatedBy) {
		if (staffProfile.getStatus() == StaffProfileStatus.RESIGNED || staffProfile.getStatus() == StaffProfileStatus.TERMINATED) {
			throw new IllegalStateException("Staff profile " + staffProfile.getId() + " has already left (" + staffProfile.getStatus() + ")");
		}
		if (exitDate.isBefore(staffProfile.getDateOfJoining())) {
			throw new InvalidRequestException("The exit date cannot be before the date of joining (" + staffProfile.getDateOfJoining() + ")");
		}
		StaffExit exit = staffExitRepository.save(new StaffExit(staffProfile, exitType, exitDate, reason, initiatedBy));

		StaffProfileStatus previous = staffProfile.getStatus();
		StaffProfileStatus newStatus = exitType == StaffExitType.TERMINATION ? StaffProfileStatus.TERMINATED : StaffProfileStatus.RESIGNED;
		staffProfile.changeStatus(newStatus);
		staffProfileRepository.save(staffProfile);

		auditLogService.record("StaffProfile", staffProfile.getId(), "EXIT", previous, newStatus);
		return exit;
	}

	/** Upserts the whole record - same "one row, update in place" convention as
	 * OrganisationConfiguration, gated by HR_PAYROLL_VIEW at the controller. */
	@Transactional
	public StaffBankDetail upsertBankDetails(StaffProfile staffProfile, String bankAccountHolderName, String bankAccountNumber,
			String bankName, String bankBranchCode, String taxIdentifier) {
		// The read side only ever returns a masked number, so an edit form echoes the mask back
		// unchanged - that must keep the stored number rather than overwrite it with asterisks.
		boolean keepStoredNumber = bankAccountNumber != null && bankAccountNumber.contains("*");
		if (bankAccountNumber != null && !keepStoredNumber && !bankAccountNumber.isBlank() && !bankAccountNumber.matches("[0-9]+")) {
			throw new InvalidRequestException("An account number can contain digits only");
		}
		return staffBankDetailRepository.findByStaffProfileId(staffProfile.getId())
				.map(existing -> {
					existing.update(bankAccountHolderName, keepStoredNumber ? existing.getBankAccountNumber() : bankAccountNumber,
							bankName, bankBranchCode, taxIdentifier);
					return staffBankDetailRepository.save(existing);
				})
				.orElseGet(() -> staffBankDetailRepository.save(
						new StaffBankDetail(staffProfile, bankAccountHolderName, bankAccountNumber, bankName, bankBranchCode, taxIdentifier)));
	}

	public LeaveType createLeaveType(String code, String name, Double defaultAnnualDays) {
		if (code == null || code.isBlank() || name == null || name.isBlank()) {
			throw new InvalidRequestException("A leave type needs both a code and a name");
		}
		if (defaultAnnualDays != null && defaultAnnualDays < 0) {
			throw new InvalidRequestException("Default annual days cannot be negative");
		}
		return leaveTypeRepository.save(new LeaveType(code, name, defaultAnnualDays));
	}

	/** Upserts - one row per staff+leaveType+academicYear, same pattern as NotificationPreference.setPreference. */
	@Transactional
	public LeaveBalance allocateBalance(StaffProfile staffProfile, LeaveType leaveType, AcademicYear academicYear, double allocatedDays) {
		if (allocatedDays < 0) {
			throw new InvalidRequestException("Allocated days cannot be negative");
		}
		return leaveBalanceRepository.findByStaffProfileIdAndLeaveTypeIdAndAcademicYearId(staffProfile.getId(), leaveType.getId(), academicYear.getId())
				.map(existing -> {
					existing.updateAllocation(allocatedDays);
					return leaveBalanceRepository.save(existing);
				})
				.orElseGet(() -> leaveBalanceRepository.save(new LeaveBalance(staffProfile, leaveType, academicYear, allocatedDays)));
	}

	public double getRemainingBalance(StaffProfile staffProfile, LeaveType leaveType, AcademicYear academicYear) {
		double allocated = leaveBalanceRepository
				.findByStaffProfileIdAndLeaveTypeIdAndAcademicYearId(staffProfile.getId(), leaveType.getId(), academicYear.getId())
				.map(LeaveBalance::getAllocatedDays)
				.orElse(0.0);
		double used = leaveRequestRepository.sumDaysByStaffProfileIdAndLeaveTypeIdAndAcademicYearIdAndStatus(
				staffProfile.getId(), leaveType.getId(), academicYear.getId(), LeaveRequestStatus.APPROVED);
		return allocated - used;
	}

	public LeaveRequest raiseLeaveRequest(StaffProfile staffProfile, LeaveType leaveType, AcademicYear academicYear,
			LocalDate startDate, LocalDate endDate, double numberOfDays, String reason) {
		if (startDate == null || endDate == null) {
			throw new InvalidRequestException("A leave request needs a start date and an end date");
		}
		if (numberOfDays <= 0) {
			throw new InvalidRequestException("Leave request numberOfDays must be positive");
		}
		if (endDate.isBefore(startDate)) {
			throw new InvalidRequestException("Leave request endDate cannot be before startDate");
		}
		// Holidays and weekly-off days are not leave days - only working days can be asked for.
		Long campusId = staffProfile.getCampus() != null ? staffProfile.getCampus().getId() : null;
		long calendarDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
		int workingDays = workingDayService.countWorkingDays(startDate, endDate, campusId);
		if (workingDays == 0) {
			throw new InvalidRequestException("Every day from " + startDate + " to " + endDate
					+ " is a holiday or weekly off - no leave is needed");
		}
		if (numberOfDays > workingDays) {
			throw new InvalidRequestException("Leave request covers " + workingDays + " working day(s) ("
					+ calendarDays + " calendar day(s), minus holidays and weekly offs) but asks for " + numberOfDays);
		}
		boolean overlaps = leaveRequestRepository.findByStaffProfileId(staffProfile.getId()).stream()
				.filter(r -> r.getStatus() == LeaveRequestStatus.PENDING || r.getStatus() == LeaveRequestStatus.APPROVED)
				.anyMatch(r -> !r.getStartDate().isAfter(endDate) && !r.getEndDate().isBefore(startDate));
		if (overlaps) {
			throw new IllegalStateException("This staff member already has a pending or approved leave request overlapping those dates");
		}
		return leaveRequestRepository.save(new LeaveRequest(staffProfile, leaveType, academicYear, startDate, endDate, numberOfDays, reason));
	}

	/** Also upserts a LEAVE StaffAttendance row for every covered working day - see
	 * ai-context/erp-system-plan.md §3.3's leave/attendance seam. Skipped for org-wide staff
	 * (no campus to attach a StaffAttendance row to); StaffLeaveStatusScheduler separately
	 * derives StaffProfileStatus.ON_LEAVE day-by-day, independent of this write. */
	@Transactional
	public LeaveRequest approve(LeaveRequest leaveRequest, Long approvedBy) {
		StaffProfile staffProfile = leaveRequest.getStaffProfile();
		double remaining = getRemainingBalance(staffProfile, leaveRequest.getLeaveType(), leaveRequest.getAcademicYear());
		if (leaveRequest.getNumberOfDays() > remaining) {
			throw new IllegalStateException("Leave request " + leaveRequest.getId() + " exceeds remaining balance of " + remaining + " days");
		}
		leaveRequest.approve(approvedBy);
		LeaveRequest saved = leaveRequestRepository.save(leaveRequest);

		if (staffProfile.getCampus() != null) {
			// Holidays and weekly offs inside the range are not leave, so they get no LEAVE row.
			for (LocalDate date : workingDayService.workingDays(leaveRequest.getStartDate(), leaveRequest.getEndDate(),
					staffProfile.getCampus().getId())) {
				markAttendanceOnLeave(staffProfile, date);
			}
		}

		auditLogService.record("LeaveRequest", leaveRequest.getId(), "APPROVE", LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED);
		return saved;
	}

	private void markAttendanceOnLeave(StaffProfile staffProfile, LocalDate date) {
		StaffAttendance attendance = staffAttendanceRepository.findByPersonIdAndAttendanceDate(staffProfile.getPerson().getId(), date)
				.orElseGet(() -> new StaffAttendance(staffProfile.getPerson(), staffProfile.getCampus(), date, AttendanceStatus.LEAVE, null, null));
		attendance.markOnLeave();
		staffAttendanceRepository.save(attendance);
	}

	public LeaveRequest reject(LeaveRequest leaveRequest, Long decidedBy) {
		leaveRequest.reject(decidedBy);
		LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
		auditLogService.record("LeaveRequest", leaveRequest.getId(), "REJECT", LeaveRequestStatus.PENDING, LeaveRequestStatus.REJECTED);
		return saved;
	}

	public LeaveRequest cancel(LeaveRequest leaveRequest) {
		leaveRequest.cancel();
		LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
		auditLogService.record("LeaveRequest", leaveRequest.getId(), "CANCEL", LeaveRequestStatus.PENDING, LeaveRequestStatus.CANCELLED);
		return saved;
	}

	/** Public/unauthenticated entry point - resolves the org from its slug and sets
	 * TenantContext itself, same bootstrap shape as PortalInviteService.claim() and
	 * PasswordResetService, since there is no bearer token yet to carry tenant context. */
	public JobApplication submitJobApplication(String organisationSlug, String applicantName, String email, String specialization,
			Integer yearsExperience) {
		Organisation organisation = organisationRepository.findBySlug(organisationSlug)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with slug " + organisationSlug));
		// Deliberately not @Transactional: the tenant identifier resolves once per Hibernate session,
		// so TenantContext must be set before the transaction that saves the row opens.
		TenantContext.set(organisation.getId(), null);
		if (applicantName == null || applicantName.isBlank()) {
			throw new InvalidRequestException("An applicant name is required");
		}
		if (email == null || !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			throw new InvalidRequestException("A valid email address is required");
		}
		if (yearsExperience != null && yearsExperience < 0) {
			throw new InvalidRequestException("Years of experience cannot be negative");
		}
		if (jobApplicationRepository.existsByEmailIgnoreCase(email.trim())) {
			throw new IllegalStateException("An application from " + email.trim() + " has already been submitted");
		}
		return jobApplicationRepository.save(new JobApplication(applicantName.trim(), email.trim(), specialization, yearsExperience));
	}

	public JobApplication approveJobApplication(JobApplication jobApplication, Long decidedBy) {
		jobApplication.approve(decidedBy);
		return jobApplicationRepository.save(jobApplication);
	}

	public JobApplication rejectJobApplication(JobApplication jobApplication, Long decidedBy) {
		jobApplication.reject(decidedBy);
		return jobApplicationRepository.save(jobApplication);
	}

	/** Re-upload supersedes any prior ACTIVE document of the same type, same pattern as StudentService.addDocument. */
	@Transactional
	public StaffDocument addDocument(StaffProfile staffProfile, String documentType, String fileReference, String fileName,
			String mimeType, LocalDate expiryDate) {
		staffDocumentRepository.findByStaffProfileIdAndDocumentTypeAndStatus(staffProfile.getId(), documentType, StaffDocumentStatus.ACTIVE)
				.ifPresent(existing -> {
					existing.supersede();
					staffDocumentRepository.save(existing);
				});
		return staffDocumentRepository.save(new StaffDocument(staffProfile, documentType, fileReference, fileName, mimeType, expiryDate));
	}

	public StaffDocument verifyDocument(StaffDocument document, DocumentVerificationStatus verificationStatus, Long verifiedBy) {
		DocumentVerificationStatus previous = document.getVerificationStatus();
		document.verify(verificationStatus, verifiedBy, Instant.now());
		StaffDocument saved = staffDocumentRepository.save(document);
		auditLogService.record("StaffDocument", document.getId(), "VERIFY", previous, verificationStatus);
		return saved;
	}
}
