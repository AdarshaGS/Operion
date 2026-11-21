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

	/** Upper bound for one allocation row: a year has at most 366 days, so more can never be taken. */
	static final double MAX_ALLOCATED_DAYS = 366;

	/** Earliest date of joining accepted: anything older is a typo, not a real hire. */
	static final LocalDate EARLIEST_DATE_OF_JOINING = LocalDate.of(1950, 1, 1);

	/** How far ahead of today a joining date may be set (an offer accepted for a future start). */
	static final int MAX_DAYS_JOINING_AHEAD = 365;

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
		return createStaffProfile(person, campus, employeeCode, designation, department, dateOfJoining, employmentType, null);
	}

	/** {@code reportingManagerId} is resolved here, inside the same transaction and before anything is written, so an unknown id
	 * fails (IllegalArgumentException) without leaving a half-created profile - and the manager goes in with the profile's one insert. */
	@Transactional
	public StaffProfile createStaffProfile(Person person, Campus campus, String employeeCode, Designation designation,
			Department department, LocalDate dateOfJoining, EmploymentType employmentType, Long reportingManagerId) {
		// The one door every staff profile goes through (create, create-with-access, hire, import), so the joining-date rule lives here.
		requireValidDateOfJoining(dateOfJoining, LocalDate.now());
		StaffProfile reportingManager = reportingManagerId == null ? null : staffProfileRepository.findById(reportingManagerId)
				.orElseThrow(() -> new IllegalArgumentException("No staff profile with id " + reportingManagerId));
		if (staffProfileRepository.findByPersonId(person.getId()).isPresent()) {
			throw new IllegalStateException("This person already has a staff profile");
		}
		if (staffProfileRepository.findByEmployeeCode(employeeCode).isPresent()) {
			throw new IllegalStateException("A staff member with the employee code " + employeeCode + " already exists");
		}
		StaffProfile newProfile = new StaffProfile(person, campus, employeeCode, designation, department, dateOfJoining, employmentType);
		newProfile.setReportingManager(reportingManager);
		StaffProfile staffProfile = staffProfileRepository.save(newProfile);
		staffAssignmentRepository.save(new StaffAssignment(staffProfile, campus, department, designation, dateOfJoining));
		return staffProfile;
	}

	/** Joining date must fall between {@link #EARLIEST_DATE_OF_JOINING} and {@code today} + {@link #MAX_DAYS_JOINING_AHEAD} days, both inclusive. */
	static void requireValidDateOfJoining(LocalDate dateOfJoining, LocalDate today) {
		if (dateOfJoining == null) {
			throw new InvalidRequestException("A date of joining is required");
		}
		if (dateOfJoining.isBefore(EARLIEST_DATE_OF_JOINING)) {
			throw new InvalidRequestException("The date of joining cannot be before " + EARLIEST_DATE_OF_JOINING);
		}
		LocalDate latest = today.plusDays(MAX_DAYS_JOINING_AHEAD);
		if (dateOfJoining.isAfter(latest)) {
			throw new InvalidRequestException("The date of joining cannot be more than " + MAX_DAYS_JOINING_AHEAD
					+ " days in the future (latest " + latest + ")");
		}
	}

	@Transactional
	public StaffProfile changeStaffStatus(StaffProfile staffProfile, StaffProfileStatus status) {
		// Leaving is a recorded event (exit record, assignment closure, pending-leave withdrawal), never a bare status flip -
		// and once it has happened the status is no longer this endpoint's to change.
		if (status == StaffProfileStatus.RESIGNED || status == StaffProfileStatus.TERMINATED) {
			throw new InvalidRequestException("Status " + status + " cannot be set directly - record the staff exit instead (POST /hr/staff/{id}/exit)");
		}
		StaffProfile locked = lockStaffProfile(staffProfile);
		if (hasLeft(locked)) {
			throw new IllegalStateException("Staff profile " + staffProfile.getId() + " has already left (" + locked.getStatus()
					+ "); its status cannot be changed");
		}
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
		StaffProfile locked = lockStaffProfile(staffProfile);
		if (hasLeft(locked)) {
			throw new InvalidRequestException("A staff member who has left (" + locked.getStatus() + ") cannot be transferred");
		}
		if (effectiveDate.isBefore(staffProfile.getDateOfJoining())) {
			throw new InvalidRequestException("The effective date cannot be before the date of joining (" + staffProfile.getDateOfJoining() + ")");
		}
		staffAssignmentRepository.findByStaffProfileIdAndStatus(staffProfile.getId(), StaffAssignmentStatus.ACTIVE)
				.filter(current -> effectiveDate.isBefore(current.getStartDate()))
				.ifPresent(current -> {
					throw new InvalidRequestException("The effective date cannot be before the current assignment started ("
							+ current.getStartDate() + ")");
				});
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
		// Same lock as leave approval, so an approval cannot slip in between the pending-leave withdrawal below and the status flip.
		StaffProfile locked = lockStaffProfile(staffProfile);
		if (hasLeft(staffProfile) || hasLeft(locked)) {
			throw new IllegalStateException("Staff profile " + staffProfile.getId() + " has already left (" + staffProfile.getStatus() + ")");
		}
		if (exitDate.isBefore(staffProfile.getDateOfJoining())) {
			throw new InvalidRequestException("The exit date cannot be before the date of joining (" + staffProfile.getDateOfJoining() + ")");
		}
		StaffExit exit = staffExitRepository.save(new StaffExit(staffProfile, exitType, exitDate, reason, initiatedBy));

		// Someone who has left holds no post: close the open assignment (never before it started)...
		staffAssignmentRepository.findByStaffProfileIdAndStatus(staffProfile.getId(), StaffAssignmentStatus.ACTIVE)
				.ifPresent(current -> {
					current.end(exitDate.isBefore(current.getStartDate()) ? current.getStartDate() : exitDate);
					staffAssignmentRepository.save(current);
				});
		// ...and withdraw leave that was still awaiting a decision and reaches the day they leave or later (a request that
		// starts before the exit date but runs past it counts too).
		leaveRequestRepository.findByStaffProfileIdAndStatus(staffProfile.getId(), LeaveRequestStatus.PENDING).stream()
				.filter(r -> !r.getEndDate().isBefore(exitDate))
				.forEach(this::cancel);

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
		// The read side returns a masked number (and tax identifier) to callers who may not see the full value, so an edit form
		// echoes the mask back unchanged - that must keep the stored value rather than overwrite it with asterisks.
		boolean keepStoredNumber = isMasked(bankAccountNumber);
		boolean keepStoredTaxIdentifier = isMasked(taxIdentifier);
		if (bankAccountNumber != null && !keepStoredNumber && !bankAccountNumber.isBlank() && !bankAccountNumber.matches("[0-9]+")) {
			throw new InvalidRequestException("An account number can contain digits only");
		}
		return staffBankDetailRepository.findByStaffProfileId(staffProfile.getId())
				.map(existing -> {
					existing.update(bankAccountHolderName, keepStoredNumber ? existing.getBankAccountNumber() : bankAccountNumber,
							bankName, bankBranchCode, keepStoredTaxIdentifier ? existing.getTaxIdentifier() : taxIdentifier);
					return staffBankDetailRepository.save(existing);
				})
				.orElseGet(() -> {
					// Nothing stored to keep: saving the mask itself would record asterisks as the real value.
					if (keepStoredNumber || keepStoredTaxIdentifier) {
						throw new InvalidRequestException("A masked account number or tax identifier cannot be saved - enter the full value");
					}
					return staffBankDetailRepository.save(
							new StaffBankDetail(staffProfile, bankAccountHolderName, bankAccountNumber, bankName, bankBranchCode, taxIdentifier));
				});
	}

	private static boolean isMasked(String value) {
		return value != null && value.contains("*");
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

	public LeaveType updateLeaveType(LeaveType leaveType, String name, Double defaultAnnualDays) {
		if (name == null || name.isBlank()) {
			throw new InvalidRequestException("A leave type needs a name");
		}
		if (defaultAnnualDays != null && defaultAnnualDays < 0) {
			throw new InvalidRequestException("Default annual days cannot be negative");
		}
		leaveType.update(name.trim(), defaultAnnualDays);
		return leaveTypeRepository.save(leaveType);
	}

	/** Upserts - one row per staff+leaveType+academicYear, same pattern as NotificationPreference.setPreference. Takes the
	 * staff member's row lock first, so concurrent allocations (and approvals) for one person run one at a time: two first-time
	 * inserts can no longer race on the unique key, and the second of two updates sees the first's row. */
	@Transactional
	public LeaveBalance allocateBalance(StaffProfile requestedStaffProfile, LeaveType leaveType, AcademicYear academicYear, double allocatedDays) {
		if (Double.isNaN(allocatedDays) || allocatedDays < 0) {
			throw new InvalidRequestException("Allocated days cannot be negative");
		}
		requireHalfDayGranularity(allocatedDays, "Allocated days");
		if (allocatedDays > MAX_ALLOCATED_DAYS) {
			throw new InvalidRequestException("Allocated days cannot exceed " + MAX_ALLOCATED_DAYS + " (days in a year)");
		}
		StaffProfile staffProfile = lockStaffProfile(requestedStaffProfile);
		if (hasLeft(staffProfile)) {
			throw new InvalidRequestException("Leave cannot be allocated to a staff member who has left (" + staffProfile.getStatus() + ")");
		}
		double used = leaveRequestRepository.sumDaysByStaffProfileIdAndLeaveTypeIdAndAcademicYearIdAndStatus(
				staffProfile.getId(), leaveType.getId(), academicYear.getId(), LeaveRequestStatus.APPROVED);
		if (allocatedDays < used) {
			throw new InvalidRequestException("Cannot allocate " + allocatedDays + " day(s): " + used
					+ " day(s) of this leave type are already approved for that academic year");
		}
		return leaveBalanceRepository.findByStaffProfileIdAndLeaveTypeIdAndAcademicYearId(staffProfile.getId(), leaveType.getId(), academicYear.getId())
				.map(existing -> {
					existing.updateAllocation(allocatedDays);
					return leaveBalanceRepository.save(existing);
				})
				.orElseGet(() -> leaveBalanceRepository.save(new LeaveBalance(staffProfile, leaveType, academicYear, allocatedDays)));
	}

	/** Leave is counted in half days: 0.5, 1, 1.5 ... Halves are exact in binary floating point, so the modulo test is exact
	 * (and NaN/Infinity fail it). */
	private static void requireHalfDayGranularity(double days, String label) {
		if ((days * 2) % 1 != 0) {
			throw new InvalidRequestException(label + " must be a multiple of 0.5 (got " + days + ")");
		}
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
		requireHalfDayGranularity(numberOfDays, "Leave request numberOfDays");
		if (endDate.isBefore(startDate)) {
			throw new InvalidRequestException("Leave request endDate cannot be before startDate");
		}
		if (staffProfile.getStatus() == StaffProfileStatus.RESIGNED || staffProfile.getStatus() == StaffProfileStatus.TERMINATED) {
			throw new InvalidRequestException("Leave cannot be requested for a staff member who has left (" + staffProfile.getStatus() + ")");
		}
		if (leaveType.getStatus() != LeaveTypeStatus.ACTIVE) {
			throw new InvalidRequestException("Leave type " + leaveType.getName() + " is not active");
		}
		if (startDate.isBefore(academicYear.getStartDate()) || endDate.isAfter(academicYear.getEndDate())) {
			throw new InvalidRequestException("Leave must fall within the academic year " + academicYear.getName() + " ("
					+ academicYear.getStartDate() + " to " + academicYear.getEndDate() + ")");
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
	public LeaveRequest approve(LeaveRequest requested, Long approvedBy) {
		// Lock order is always staff profile, then the request. The staff lock serialises approvals of DIFFERENT requests
		// for the same person (each would otherwise read the same remaining balance and overdraw it) and also allocation,
		// exit and transfer. Nothing may read leave data in this transaction before the staff lock is held: MySQL's
		// repeatable-read snapshot starts at the first plain read, which must come after any rival's commit.
		StaffProfile staffProfile = lockStaffProfile(requested.getStaffProfile());
		// Re-read under a row lock so two concurrent approvals of one request serialise: the loser
		// sees the winner's APPROVED status and fails the pending check instead of double-counting.
		LeaveRequest leaveRequest = leaveRequestRepository.findWithLockById(requested.getId())
				.orElseThrow(() -> new IllegalArgumentException("No leave request with id " + requested.getId()));
		leaveRequest.requirePending();
		if (hasLeft(staffProfile)) {
			throw new IllegalStateException("Leave request " + leaveRequest.getId() + " cannot be approved: the staff member has left ("
					+ staffProfile.getStatus() + ")");
		}
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

	/** Re-reads the staff member under SELECT ... FOR UPDATE; the returned row is the authority on status, the argument may be stale. */
	private StaffProfile lockStaffProfile(StaffProfile staffProfile) {
		return staffProfileRepository.findWithLockById(staffProfile.getId())
				.orElseThrow(() -> new IllegalArgumentException("No staff profile with id " + staffProfile.getId()));
	}

	private static boolean hasLeft(StaffProfile staffProfile) {
		return staffProfile.getStatus() == StaffProfileStatus.RESIGNED || staffProfile.getStatus() == StaffProfileStatus.TERMINATED;
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

	/** Returns true when a new application was stored, false when the email had already applied (callers must not
	 * surface that difference to the public). Public/unauthenticated entry point - resolves the org from its slug and sets
	 * TenantContext itself, same bootstrap shape as PortalInviteService.claim() and
	 * PasswordResetService, since there is no bearer token yet to carry tenant context. */
	public boolean submitJobApplication(String organisationSlug, String applicantName, String email, String specialization,
			Integer yearsExperience) {
		Organisation organisation = organisationRepository.findBySlug(organisationSlug)
				.orElseThrow(() -> new IllegalArgumentException("No such organisation"));
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
			// Deduplicated server-side but reported exactly like a fresh submission: this endpoint is public, and a
			// distinct "already applied" answer would let anyone probe whether a given email has applied here.
			return false;
		}
		jobApplicationRepository.save(new JobApplication(applicantName.trim(), email.trim(), specialization, yearsExperience));
		return true;
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
