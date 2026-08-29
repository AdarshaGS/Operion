package com.operion.hr;

import java.time.Instant;
import java.time.LocalDate;

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

	public HrService(StaffProfileRepository staffProfileRepository, LeaveTypeRepository leaveTypeRepository,
			LeaveBalanceRepository leaveBalanceRepository, LeaveRequestRepository leaveRequestRepository,
			StaffDocumentRepository staffDocumentRepository, JobApplicationRepository jobApplicationRepository,
			OrganisationRepository organisationRepository) {
		this.staffProfileRepository = staffProfileRepository;
		this.leaveTypeRepository = leaveTypeRepository;
		this.leaveBalanceRepository = leaveBalanceRepository;
		this.leaveRequestRepository = leaveRequestRepository;
		this.staffDocumentRepository = staffDocumentRepository;
		this.jobApplicationRepository = jobApplicationRepository;
		this.organisationRepository = organisationRepository;
	}

	public StaffProfile createStaffProfile(Person person, Campus campus, String employeeCode, Designation designation,
			Department department, LocalDate dateOfJoining, EmploymentType employmentType) {
		return staffProfileRepository.save(new StaffProfile(person, campus, employeeCode, designation, department, dateOfJoining, employmentType));
	}

	public StaffProfile changeStaffStatus(StaffProfile staffProfile, StaffProfileStatus status) {
		staffProfile.changeStatus(status);
		return staffProfileRepository.save(staffProfile);
	}

	public LeaveType createLeaveType(String code, String name, Double defaultAnnualDays) {
		return leaveTypeRepository.save(new LeaveType(code, name, defaultAnnualDays));
	}

	/** Upserts - one row per staff+leaveType+academicYear, same pattern as NotificationPreference.setPreference. */
	@Transactional
	public LeaveBalance allocateBalance(StaffProfile staffProfile, LeaveType leaveType, AcademicYear academicYear, double allocatedDays) {
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
		return leaveRequestRepository.save(new LeaveRequest(staffProfile, leaveType, academicYear, startDate, endDate, numberOfDays, reason));
	}

	@Transactional
	public LeaveRequest approve(LeaveRequest leaveRequest, Long approvedBy) {
		double remaining = getRemainingBalance(leaveRequest.getStaffProfile(), leaveRequest.getLeaveType(), leaveRequest.getAcademicYear());
		if (leaveRequest.getNumberOfDays() > remaining) {
			throw new IllegalStateException("Leave request " + leaveRequest.getId() + " exceeds remaining balance of " + remaining + " days");
		}
		leaveRequest.approve(approvedBy);
		return leaveRequestRepository.save(leaveRequest);
	}

	public LeaveRequest reject(LeaveRequest leaveRequest, Long decidedBy) {
		leaveRequest.reject(decidedBy);
		return leaveRequestRepository.save(leaveRequest);
	}

	public LeaveRequest cancel(LeaveRequest leaveRequest) {
		leaveRequest.cancel();
		return leaveRequestRepository.save(leaveRequest);
	}

	/** Public/unauthenticated entry point - resolves the org from its slug and sets
	 * TenantContext itself, same bootstrap shape as PortalInviteService.claim() and
	 * PasswordResetService, since there is no bearer token yet to carry tenant context. */
	@Transactional
	public JobApplication submitJobApplication(String organisationSlug, String applicantName, String email, String specialization,
			Integer yearsExperience) {
		Organisation organisation = organisationRepository.findBySlug(organisationSlug)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with slug " + organisationSlug));
		TenantContext.set(organisation.getId(), null);
		return jobApplicationRepository.save(new JobApplication(applicantName, email, specialization, yearsExperience));
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
	public StaffDocument addDocument(StaffProfile staffProfile, String documentType, String fileReference, String fileName, String mimeType) {
		staffDocumentRepository.findByStaffProfileIdAndDocumentTypeAndStatus(staffProfile.getId(), documentType, StaffDocumentStatus.ACTIVE)
				.ifPresent(StaffDocument::supersede);
		return staffDocumentRepository.save(new StaffDocument(staffProfile, documentType, fileReference, fileName, mimeType));
	}

	public StaffDocument verifyDocument(StaffDocument document, DocumentVerificationStatus verificationStatus, Long verifiedBy) {
		document.verify(verificationStatus, verifiedBy, Instant.now());
		return staffDocumentRepository.save(document);
	}
}
