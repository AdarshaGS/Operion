package com.operion.hr.api;

import java.util.List;

import com.operion.hr.DocumentVerificationStatus;
import com.operion.hr.EmploymentType;
import com.operion.hr.HrService;
import com.operion.hr.StaffDocument;
import com.operion.hr.StaffDocumentRepository;
import com.operion.hr.StaffDocumentStatus;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.hr.StaffProfileStatus;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hr/staff")
public class StaffProfileController {

	private final HrService hrService;
	private final StaffProfileRepository staffProfileRepository;
	private final StaffDocumentRepository staffDocumentRepository;
	private final PersonRepository personRepository;
	private final CampusRepository campusRepository;

	public StaffProfileController(HrService hrService, StaffProfileRepository staffProfileRepository,
			StaffDocumentRepository staffDocumentRepository, PersonRepository personRepository, CampusRepository campusRepository) {
		this.hrService = hrService;
		this.staffProfileRepository = staffProfileRepository;
		this.staffDocumentRepository = staffDocumentRepository;
		this.personRepository = personRepository;
		this.campusRepository = campusRepository;
	}

	@PostMapping
	public StaffProfileResponse create(@RequestBody CreateStaffProfileRequest request) {
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));
		Campus campus = request.campusId() == null ? null : campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));
		StaffProfile staffProfile = hrService.createStaffProfile(person, campus, request.employeeCode(), request.designation(),
				request.department(), request.dateOfJoining(), EmploymentType.valueOf(request.employmentType()));
		return StaffProfileResponse.from(staffProfile);
	}

	@GetMapping
	public List<StaffProfileResponse> list(@RequestParam(required = false) Long campusId) {
		List<StaffProfile> profiles = campusId != null ? staffProfileRepository.findByCampusId(campusId)
				: staffProfileRepository.findByStatus(StaffProfileStatus.ACTIVE);
		return profiles.stream().map(StaffProfileResponse::from).toList();
	}

	@PostMapping("/{id}/status")
	public StaffProfileResponse changeStatus(@PathVariable Long id, @RequestBody ChangeStaffStatusRequest request) {
		return StaffProfileResponse.from(hrService.changeStaffStatus(findStaffProfile(id), StaffProfileStatus.valueOf(request.status())));
	}

	@PostMapping("/{id}/documents")
	public StaffDocumentResponse addDocument(@PathVariable Long id, @RequestBody AddStaffDocumentRequest request) {
		StaffProfile staffProfile = findStaffProfile(id);
		StaffDocument document = hrService.addDocument(
				staffProfile, request.documentType(), request.fileReference(), request.fileName(), request.mimeType());
		return StaffDocumentResponse.from(document);
	}

	@GetMapping("/{id}/documents")
	public List<StaffDocumentResponse> listDocuments(@PathVariable Long id) {
		return staffDocumentRepository.findByStaffProfileIdAndStatus(id, StaffDocumentStatus.ACTIVE).stream()
				.map(StaffDocumentResponse::from)
				.toList();
	}

	@PostMapping("/documents/{documentId}/verify")
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
