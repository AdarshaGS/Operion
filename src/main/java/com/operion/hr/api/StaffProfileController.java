package com.operion.hr.api;

import com.operion.common.EnumParam;
import com.operion.common.TenantContext;
import com.operion.common.InvalidRequestException;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import com.operion.authorization.PermissionChecker;
import com.operion.authorization.RequirePermission;
import com.operion.common.imports.ImportRowResult;
import com.operion.hr.DocumentVerificationStatus;
import com.operion.hr.EmploymentType;
import com.operion.hr.HrService;
import com.operion.hr.StaffAccessService;
import com.operion.hr.StaffAssignmentRepository;
import com.operion.hr.StaffBankDetailRepository;
import com.operion.hr.StaffDocument;
import com.operion.hr.StaffDocumentRepository;
import com.operion.hr.StaffDocumentStatus;
import com.operion.hr.StaffExitRepository;
import com.operion.hr.StaffExitType;
import com.operion.hr.StaffImportService;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.hr.StaffProfileStatus;
import com.operion.identity.Person;
import com.operion.identity.api.StaffInviteResponse;
import com.operion.identity.auth.StaffInviteService;
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
import org.springframework.web.multipart.MultipartFile;

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
	private final StaffImportService staffImportService;
	private final StaffAccessService staffAccessService;
	private final PermissionChecker permissionChecker;

	/** Who may see whether a staff member can sign in, and which role their login holds - the same people who may list memberships. */
	private static final List<String> ACCESS_DETAIL_CODES = List.of("MEMBERSHIP_VIEW", "MEMBERSHIP_MANAGE");

	/** Who may see (and so edit) a staff member's full bank account number; payroll viewers without it get the masked form. */
	private static final List<String> FULL_BANK_DETAIL_CODES = List.of("HR_STAFF_MANAGE");

	public StaffProfileController(HrService hrService, StaffProfileRepository staffProfileRepository,
			StaffDocumentRepository staffDocumentRepository, StaffAssignmentRepository staffAssignmentRepository,
			StaffExitRepository staffExitRepository, StaffBankDetailRepository staffBankDetailRepository,
			PersonRepository personRepository, CampusRepository campusRepository, DesignationRepository designationRepository,
			DepartmentRepository departmentRepository, StaffImportService staffImportService, StaffAccessService staffAccessService,
			PermissionChecker permissionChecker) {
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
		this.staffImportService = staffImportService;
		this.staffAccessService = staffAccessService;
		this.permissionChecker = permissionChecker;
	}

	@PostMapping
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffProfileResponse create(@Valid @RequestBody CreateStaffProfileRequest request) {
		EmploymentType employmentType = EnumParam.parse(EmploymentType.class, request.employmentType());
		requireField(request.personId(), "personId");
		requireField(request.designationId(), "designationId");
		requireField(request.dateOfJoining(), "dateOfJoining");
		if (request.employeeCode() == null || request.employeeCode().isBlank()) {
			throw new InvalidRequestException("employeeCode is required");
		}
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));
		Campus campus = request.campusId() == null ? null : campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));
		Designation designation = designationRepository.findById(request.designationId())
				.orElseThrow(() -> new IllegalArgumentException("No designation with id " + request.designationId()));
		Department department = request.departmentId() == null ? null : departmentRepository.findById(request.departmentId())
				.orElseThrow(() -> new IllegalArgumentException("No department with id " + request.departmentId()));
		if (request.access() != null) {
			// Staff profile and login are created in one transaction - neither survives without the other.
			StaffAccessService.AccessResult result = staffAccessService.createStaffWithAccess(person, campus, request.employeeCode(),
					designation, department, request.dateOfJoining(), employmentType, request.reportingManagerId(), request.access(),
					TenantContext.getActorId());
			return accessResponse(result);
		}
		// The reporting manager is resolved inside the same transaction as the profile insert, so an unknown id writes nothing.
		return respond(hrService.createStaffProfile(person, campus, request.employeeCode(), designation, department,
				request.dateOfJoining(), employmentType, request.reportingManagerId()));
	}

	/** Optional login for an existing staff member: needs HR_STAFF_MANAGE (method gate) AND MEMBERSHIP_MANAGE (checked by
	 * the service). 409 if they already have access or have left. Returns the claim token in {@code invite}. */
	@PostMapping("/{id}/access")
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffProfileResponse giveAccess(@PathVariable Long id, @Valid @RequestBody StaffAccessRequest request) {
		return accessResponse(staffAccessService.giveAccess(findStaffProfile(id), request, TenantContext.getActorId()));
	}

	@PostMapping("/{id}/transfer")
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffProfileResponse transfer(@PathVariable Long id, @RequestBody TransferStaffRequest request) {
		StaffProfile staffProfile = findStaffProfile(id);
		requireField(request.designationId(), "designationId");
		requireField(request.effectiveDate(), "effectiveDate");
		Campus campus = request.campusId() == null ? null : campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));
		Designation designation = designationRepository.findById(request.designationId())
				.orElseThrow(() -> new IllegalArgumentException("No designation with id " + request.designationId()));
		Department department = request.departmentId() == null ? null : departmentRepository.findById(request.departmentId())
				.orElseThrow(() -> new IllegalArgumentException("No department with id " + request.departmentId()));
		return respond(hrService.transfer(staffProfile, campus, department, designation, request.effectiveDate()));
	}

	@GetMapping("/{id}/assignments")
	public List<StaffAssignmentResponse> listAssignments(@PathVariable Long id) {
		return staffAssignmentRepository.findByStaffProfileIdOrderByStartDateDesc(id).stream()
				.map(StaffAssignmentResponse::from)
				.toList();
	}

	@PostMapping("/{id}/exit")
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffExitResponse recordExit(@PathVariable Long id, @Valid @RequestBody RecordStaffExitRequest request) {
		StaffProfile staffProfile = findStaffProfile(id);
		StaffExitType exitType = EnumParam.parse(StaffExitType.class, request.exitType());
		requireField(request.exitDate(), "exitDate");
		return StaffExitResponse.from(staffAccessService.recordExit(staffProfile, exitType,
				request.exitDate(), request.reason(), TenantContext.getActorId()));
	}

	@GetMapping("/{id}/exits")
	public List<StaffExitResponse> listExits(@PathVariable Long id) {
		return staffExitRepository.findByStaffProfileId(id).stream().map(StaffExitResponse::from).toList();
	}

	@GetMapping("/{id}/bank-details")
	@RequirePermission("HR_PAYROLL_VIEW")
	public StaffBankDetailResponse getBankDetails(@PathVariable Long id) {
		boolean full = seesFullBankDetails();
		return staffBankDetailRepository.findByStaffProfileId(id).map(detail -> StaffBankDetailResponse.from(detail, full)).orElse(null);
	}

	@PostMapping("/{id}/bank-details")
	@RequirePermission("HR_PAYROLL_VIEW")
	public StaffBankDetailResponse upsertBankDetails(@PathVariable Long id, @Valid @RequestBody UpsertStaffBankDetailsRequest request) {
		StaffProfile staffProfile = findStaffProfile(id);
		return StaffBankDetailResponse.from(hrService.upsertBankDetails(staffProfile, request.bankAccountHolderName(),
				request.bankAccountNumber(), request.bankName(), request.bankBranchCode(), request.taxIdentifier()), seesFullBankDetails());
	}

	@GetMapping
	public List<StaffProfileResponse> list(@RequestParam(required = false) Long campusId) {
		// ON_LEAVE counts as "currently on the roster" here, same as ACTIVE - it's a
		// temporary, scheduler-derived state (StaffLeaveStatusScheduler), not an exit.
		// RESIGNED/TERMINATED stay excluded from this default roster view; get(id) below is
		// the way to look up one specific profile regardless of status (e.g. StaffDetailPage
		// after an exit is recorded).
		List<StaffProfileStatus> onRoster = List.of(StaffProfileStatus.ACTIVE, StaffProfileStatus.ON_LEAVE);
		List<StaffProfile> profiles = campusId != null ? staffProfileRepository.findByCampusIdAndStatusIn(campusId, onRoster)
				: staffProfileRepository.findByStatusIn(onRoster);
		boolean detail = seesAccessDetail();
		if (!detail) {
			// Not theirs to see: no membership lookup at all, and the access block is omitted from every row.
			return profiles.stream().map(profile -> StaffProfileResponse.from(profile, null)).toList();
		}
		// One membership query for the whole roster, not one per row.
		Map<Long, StaffAccessSummary> access = staffAccessService.summariseAll(
				profiles.stream().map(profile -> profile.getPerson().getId()).collect(java.util.stream.Collectors.toSet()));
		return profiles.stream()
				.map(profile -> StaffProfileResponse.from(profile, access.getOrDefault(profile.getPerson().getId(), StaffAccessSummary.NONE)))
				.toList();
	}

	@GetMapping("/{id}")
	public StaffProfileResponse get(@PathVariable Long id) {
		return respond(findStaffProfile(id));
	}

	@PostMapping("/{id}/status")
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffProfileResponse changeStatus(@PathVariable Long id, @RequestBody ChangeStaffStatusRequest request) {
		return respond(hrService.changeStaffStatus(findStaffProfile(id), EnumParam.parse(StaffProfileStatus.class, request.status())));
	}

	@PostMapping("/{id}/documents")
	@RequirePermission("HR_STAFF_MANAGE")
	public StaffDocumentResponse addDocument(@PathVariable Long id, @Valid @RequestBody AddStaffDocumentRequest request) {
		StaffProfile staffProfile = findStaffProfile(id);
		for (String[] field : new String[][] {{"documentType", request.documentType()}, {"fileReference", request.fileReference()}, {"fileName", request.fileName()}}) {
			if (field[1] == null || field[1].isBlank()) {
				throw new InvalidRequestException(field[0] + " is required");
			}
		}
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
				hrService.verifyDocument(document, EnumParam.parse(DocumentVerificationStatus.class, request.verificationStatus()), TenantContext.getActorId()));
	}

	/** Bulk staff import (validate-then-confirm) - reuses the same Person+StaffProfile write
	 * path as create() above, one row at a time; see StaffImportService/StaffRowImportService
	 * for the per-row transaction isolation that makes a partial import safe. */
	@PostMapping("/import")
	@RequirePermission("HR_STAFF_MANAGE")
	public List<ImportRowResult> importFile(@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "false") boolean validateOnly) {
		return staffImportService.importFile(file, validateOnly);
	}

	/** Inherits this controller's class-level HR_VIEW gate, same reuse-not-a-new-permission
	 * convention as StudentController.export(). */
	@GetMapping("/export")
	public List<StaffExportResponse> export() {
		return staffProfileRepository.findAll().stream().map(StaffExportResponse::from).toList();
	}

	/** {@code access} is null (omitted) for callers who hold neither MEMBERSHIP_VIEW nor MEMBERSHIP_MANAGE. */
	private StaffProfileResponse respond(StaffProfile staffProfile) {
		StaffAccessSummary access = seesAccessDetail() ? staffAccessService.summarise(staffProfile.getPerson().getId()) : null;
		return StaffProfileResponse.from(staffProfile, access);
	}

	/** Response for the calls that just issued a login - the caller holds MEMBERSHIP_MANAGE, so detail is theirs to see. */
	private StaffProfileResponse accessResponse(StaffAccessService.AccessResult result) {
		StaffInviteService.IssuedInvite invite = result.invite();
		return StaffProfileResponse.from(result.staffProfile(), staffAccessService.summarise(result.staffProfile().getPerson().getId()),
				new StaffInviteResponse(invite.userId(), invite.inviteId(), invite.rawToken(), invite.expiresAt(), invite.emailSent()));
	}

	private boolean seesAccessDetail() {
		return permissionChecker.holdsAny(TenantContext.getActorId(), ACCESS_DETAIL_CODES);
	}

	private boolean seesFullBankDetails() {
		return permissionChecker.holdsAny(TenantContext.getActorId(), FULL_BANK_DETAIL_CODES);
	}

	private static void requireField(Object value, String field) {
		if (value == null) {
			throw new InvalidRequestException(field + " is required");
		}
	}

	private StaffProfile findStaffProfile(Long id) {
		return staffProfileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No staff profile with id " + id));
	}
}
