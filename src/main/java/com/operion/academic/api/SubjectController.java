package com.operion.academic.api;

import java.util.List;

import com.operion.academic.AcademicService;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.academic.SubjectStatus;
import com.operion.authorization.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subjects")
@RequirePermission("SUBJECT_VIEW")
public class SubjectController {

	private final AcademicService academicService;
	private final SubjectRepository subjectRepository;

	public SubjectController(AcademicService academicService, SubjectRepository subjectRepository) {
		this.academicService = academicService;
		this.subjectRepository = subjectRepository;
	}

	@PostMapping
	@RequirePermission("SUBJECT_MANAGE")
	public SubjectResponse create(@RequestBody CreateSubjectRequest request) {
		return SubjectResponse.from(academicService.createSubject(request.name(), request.code()));
	}

	@GetMapping
	public List<SubjectResponse> list() {
		return subjectRepository.findAll().stream().map(SubjectResponse::from).toList();
	}

	@PostMapping("/{id}/status")
	@RequirePermission("SUBJECT_MANAGE")
	public SubjectResponse changeStatus(@PathVariable Long id, @RequestBody ChangeStatusRequest request) {
		Subject subject = subjectRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No subject with id " + id));
		return SubjectResponse.from(academicService.changeSubjectStatus(subject, SubjectStatus.valueOf(request.status())));
	}
}
