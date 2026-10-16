package com.operion.student.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.operion.common.api.PageResponse;
import com.operion.authorization.RequirePermission;
import com.operion.common.EnumParam;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.student.Applicant;
import com.operion.student.ApplicantRepository;
import com.operion.student.ApplicantService;
import com.operion.student.ApplicantStatus;
import com.operion.student.Student;
import com.operion.student.StudentRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reuses STUDENT_VIEW/STUDENT_MANAGE - an applicant is pre-admission student data, not
 * a distinct permission domain. */
@RestController
@RequestMapping("/api/v1/applicants")
@RequirePermission("STUDENT_VIEW")
public class ApplicantController {

	private final ApplicantService applicantService;
	private final ApplicantRepository applicantRepository;
	private final PersonRepository personRepository;
	private final StudentRepository studentRepository;

	public ApplicantController(ApplicantService applicantService, ApplicantRepository applicantRepository,
			PersonRepository personRepository, StudentRepository studentRepository) {
		this.studentRepository = studentRepository;
		this.applicantService = applicantService;
		this.applicantRepository = applicantRepository;
		this.personRepository = personRepository;
	}

	@PostMapping
	@RequirePermission("STUDENT_MANAGE")
	public ApplicantResponse inquire(@Valid @RequestBody CreateApplicantRequest request) {
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));
		if (studentRepository.findByPersonId(person.getId()).isPresent()) {
			throw new IllegalStateException("This person is already a student");
		}
		Applicant applicant = applicantService.inquire(person, request.inquiryDate(), request.source(), request.notes());
		return ApplicantResponse.from(applicant);
	}

	@GetMapping
	public PageResponse<ApplicantResponse> list(@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
				Sort.by(Sort.Direction.DESC, "inquiryDate", "id"));
		Page<Applicant> result;
		if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
			result = applicantRepository.findAll(pageable);
		} else {
			result = applicantRepository.findByStatus(EnumParam.parse(ApplicantStatus.class, status.toUpperCase()), pageable);
		}
		Map<Long, Long> studentIdByPerson = studentRepository
				.findByPersonIdIn(result.stream().filter(a -> a.getStatus() == ApplicantStatus.CONVERTED).map(a -> a.getPerson().getId()).toList())
				.stream().collect(Collectors.toMap(s -> s.getPerson().getId(), Student::getId));
		return PageResponse.from(result.map(a -> ApplicantResponse.from(a, studentIdByPerson.get(a.getPerson().getId()))));
	}

	@GetMapping("/status-counts")
	public Map<String, Long> statusCounts() {
		return applicantService.statusCounts();
	}

	@PostMapping("/{id}/start-review")
	@RequirePermission("STUDENT_MANAGE")
	public ApplicantResponse startReview(@PathVariable Long id) {
		return ApplicantResponse.from(applicantService.startReview(findOrThrow(id)));
	}

	@PostMapping("/{id}/reject")
	@RequirePermission("STUDENT_MANAGE")
	public ApplicantResponse reject(@PathVariable Long id, @Valid @RequestBody RejectApplicantRequest request) {
		return ApplicantResponse.from(applicantService.reject(findOrThrow(id), request.reason()));
	}

	@GetMapping("/{id}/documents")
	public List<ApplicantDocumentEntry> documents(@PathVariable Long id) {
		findOrThrow(id);
		return toEntries(applicantService.documents(id));
	}

	@PutMapping("/{id}/documents")
	@RequirePermission("STUDENT_MANAGE")
	public List<ApplicantDocumentEntry> updateDocuments(@PathVariable Long id,
			@Valid @RequestBody List<@Valid ApplicantDocumentEntry> entries) {
		Applicant applicant = findOrThrow(id);
		Map<String, Boolean> changes = entries.stream()
				.collect(Collectors.toMap(ApplicantDocumentEntry::docType, ApplicantDocumentEntry::received, (a, b) -> b));
		return toEntries(applicantService.updateDocuments(applicant, changes));
	}

	private static List<ApplicantDocumentEntry> toEntries(Map<String, Boolean> documents) {
		return documents.entrySet().stream().map(e -> new ApplicantDocumentEntry(e.getKey(), e.getValue())).toList();
	}

	@PostMapping("/{id}/convert")
	@RequirePermission("STUDENT_MANAGE")
	public StudentResponse convert(@PathVariable Long id, @Valid @RequestBody ConvertApplicantRequest request) {
		Applicant applicant = findOrThrow(id);
		Student student = applicantService.convert(applicant, request.admissionNumber(), request.admissionDate(),
				request.admissionSource(), request.previousSchool(), request.tcNumber(), request.entranceScore(),
				request.bloodGroup(), request.category(), request.nationality(), request.remarks(),
				request.medicalAlerts(), request.emergencyContactName(), request.emergencyContactPhone());
		return StudentResponse.from(student);
	}

	private Applicant findOrThrow(Long id) {
		return applicantRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No applicant with id " + id));
	}
}
