package com.operion.student.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.student.Applicant;
import com.operion.student.ApplicantRepository;
import com.operion.student.ApplicantService;
import com.operion.student.Student;
import jakarta.validation.Valid;
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

	public ApplicantController(ApplicantService applicantService, ApplicantRepository applicantRepository,
			PersonRepository personRepository) {
		this.applicantService = applicantService;
		this.applicantRepository = applicantRepository;
		this.personRepository = personRepository;
	}

	@PostMapping
	@RequirePermission("STUDENT_MANAGE")
	public ApplicantResponse inquire(@Valid @RequestBody CreateApplicantRequest request) {
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));
		Applicant applicant = applicantService.inquire(person, request.inquiryDate(), request.source(), request.notes());
		return ApplicantResponse.from(applicant);
	}

	@GetMapping
	public List<ApplicantResponse> list() {
		return applicantRepository.findAll().stream().map(ApplicantResponse::from).toList();
	}

	@PostMapping("/{id}/reject")
	@RequirePermission("STUDENT_MANAGE")
	public ApplicantResponse reject(@PathVariable Long id) {
		Applicant applicant = findOrThrow(id);
		return ApplicantResponse.from(applicantService.reject(applicant));
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
