package com.operion.hr.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.hr.DocumentVerificationStatus;
import com.operion.hr.EmploymentType;
import com.operion.hr.HrService;
import com.operion.hr.StaffAssignmentRepository;
import com.operion.hr.StaffBankDetailRepository;
import com.operion.hr.StaffDocument;
import com.operion.hr.StaffDocumentRepository;
import com.operion.hr.StaffDocumentStatus;
import com.operion.hr.StaffExitRepository;
import com.operion.hr.StaffExitType;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.hr.StaffProfileStatus;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Department;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hr/staff")
@RequirePermission("HR_VIEW")
public class StaffProfileController {

	private final HrService hrService;
	private final StaffProfileRepository staffProfileRepository;
	private final StaffDocumentRepository staffDocumentRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final StaffExitRepository staffExitRepository;
	private final StaffBankDetailRepository staffBankDetailRepository;
	private final PersonRepository personRepository;
	private final CampusRepository campusRepository;
	private final DesignationRepository designationRepository;
	private final DepartmentRepository departmentRepository;

	public StaffProfileController(HrService hrService, StaffProfileRepository staffProfileRepository,
			StaffDocumentRepository staffDocumentRepository, StaffAssignmentRepository staffAssignmentRepository,
			StaffExitRepository staffExitRepository, StaffBankDetailRepository staffBankDetailRepository,
			PersonRepository personRepository, CampusRepository campusRepository, DesignationRepository designationRepository,
			DepartmentRepository departmentRepository) {
		this.hrService = hrService;
		this.staffProfileRepository = staffProfileRepository;
		this.staffDocumentRepository = staffDocumentRepository;
		this.staffAssignmentRepository = staffAssignmentRepository;
		this.staffExitRepository = staffExitRepository;
		this.staffBankDetailRepository = staffBankDetailRepository;
		this.personRepository = personRepository;
		this.campusRepository = campusRepository;
		this.designationRepository = designationRepository;
		this.departmentRepository = departmentRepository;
	}

	@PostMapping
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffProfileResponse create(@RequestBody CreateStaffProfileRequest request) {
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));
		Campus campus = request.campusId() == null ? null : campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));
		Designation designation = designationRepository.findById(request.designationId())
				.orElseThrow(() -> new IllegalArgumentException("No designation with id " + request.designationId()));
		Department department = request.departmentId() == null ? null : departmentRepository.findById(request.departmentId())
				.orElseThrow(() -> new IllegalArgumentException("No department with id " + request.departmentId()));
		StaffProfile staffProfile = hrService.createStaffProfile(person, campus, request.employeeCode(), designation,
				department, request.dateOfJoining(), EmploymentType.valueOf(request.employmentType()));
		if (request.reportingManagerId() != null) {
			staffProfile.setReportingManager(findStaffProfile(request.reportingManagerId()));
			staffProfile = staffProfileRepository.save(staffProfile);
		}
		return StaffProfileResponse.from(staffProfile);
	}

	@PostMapping("/{id}/transfer")
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffProfileResponse transfer(@PathVariable Long id, @RequestBody TransferStaffRequest request) {
		StaffProfile staffProfile = findStaffProfile(id);
		Campus campus = request.campusId() == null ? null : campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));
		Designation designation = designationRepository.findById(request.designationId())
				.orElseThrow(() -> new IllegalArgumentException("No designation with id " + request.designationId()));
		Department department = request.departmentId() == null ? null : departmentRepository.findById(request.departmentId())
				.orElseThrow(() -> new IllegalArgumentException("No department with id " + request.departmentId()));
		return StaffProfileResponse.from(hrService.transfer(staffProfile, campus, department, designation, request.effectiveDate()));
	}

	@GetMapping("/{id}/assignments")
	public List<StaffAssignmentResponse> listAssignments(@PathVariable Long id) {
		return staffAssignmentRepository.findByStaffProfileIdOrderByStartDateDesc(id).stream()
				.map(StaffAssignmentResponse::from)
				.toList();
	}

	@PostMapping("/{id}/exit")
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffExitResponse recordExit(@PathVariable Long id, @RequestBody RecordStaffExitRequest request) {
		StaffProfile staffProfile = findStaffProfile(id);
		return StaffExitResponse.from(hrService.recordExit(staffProfile, StaffExitType.valueOf(request.exitType()),
				request.exitDate(), request.reason(), request.initiatedBy()));
	}

	@GetMapping("/{id}/exits")
	public List<StaffExitResponse> listExits(@PathVariable Long id) {
		return staffExitRepository.findByStaffProfileId(id).stream().map(StaffExitResponse::from).toList();
	}

	@GetMapping("/{id}/bank-details")
	@RequirePermission("HR_PAYROLL_VIEW")
	public StaffBankDetailResponse getBankDetails(@PathVariable Long id) {
		return staffBankDetailRepository.findByStaffProfileId(id).map(StaffBankDetailResponse::from).orElse(null);
	}

	@PostMapping("/{id}/bank-details")
	@RequirePermission("HR_PAYROLL_VIEW")
	public StaffBankDetailResponse upsertBankDetails(@PathVariable Long id, @RequestBody UpsertStaffBankDetailsRequest request) {
		StaffProfile staffProfile = findStaffProfile(id);
		return StaffBankDetailResponse.from(hrService.upsertBankDetails(staffProfile, request.bankAccountHolderName(),
				request.bankAccountNumber(), request.bankName(), request.bankBranchCode(), request.taxIdentifier()));
	}

	@GetMapping
	public List<StaffProfileResponse> list(@RequestParam(required = false) Long campusId) {
		// ON_LEAVE counts as "currently on the roster" here, same as ACTIVE - it's a
		// temporary, scheduler-derived state (StaffLeaveStatusScheduler), not an exit.
		// RESIGNED/TERMINATED stay excluded from this default roster view; get(id) below is
		// the way to look up one specific profile regardless of status (e.g. StaffDetailPage
		// after an exit is recorded).
		List<StaffProfile> profiles = campusId != null ? staffProfileRepository.findByCampusId(campusId)
				: staffProfileRepository.findByStatusIn(List.of(StaffProfileStatus.ACTIVE, StaffProfileStatus.ON_LEAVE));
		return profiles.stream().map(StaffProfileResponse::from).toList();
	}

	@GetMapping("/{id}")
	public StaffProfileResponse get(@PathVariable Long id) {
		return StaffProfileResponse.from(findStaffProfile(id));
	}

	@PostMapping("/{id}/status")
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffProfileResponse changeStatus(@PathVariable Long id, @RequestBody ChangeStaffStatusRequest request) {
		return StaffProfileResponse.from(hrService.changeStaffStatus(findStaffProfile(id), StaffProfileStatus.valueOf(request.status())));
	}

	@PostMapping("/{id}/documents")
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffDocumentResponse addDocument(@PathVariable Long id, @RequestBody AddStaffDocumentRequest request) {
		StaffProfile staffProfile = findStaffProfile(id);
		StaffDocument document = hrService.addDocument(
				staffProfile, request.documentType(), request.fileReference(), request.fileName(), request.mimeType(), request.expiryDate());
		return StaffDocumentResponse.from(document);
	}

	@GetMapping("/{id}/documents")
	public List<StaffDocumentResponse> listDocuments(@PathVariable Long id) {
		return staffDocumentRepository.findByStaffProfileIdAndStatus(id, StaffDocumentStatus.ACTIVE).stream()
				.map(StaffDocumentResponse::from)
				.toList();
	}

	@PostMapping("/documents/{documentId}/verify")
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffDocumentResponse verifyDocument(@PathVariable Long documentId, @RequestBody VerifyStaffDocumentRequest request) {
		StaffDocument document = staffDocumentRepository.findById(documentId)
				.orElseThrow(() -> new IllegalArgumentException("No staff document with id " + documentId));
		return StaffDocumentResponse.from(
				hrService.verifyDocument(document, DocumentVerificationStatus.valueOf(request.verificationStatus()), request.verifiedBy()));
	}

	private StaffProfile findStaffProfile(Long id) {
		return staffProfileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No staff profile with id " + id));
	}
}
