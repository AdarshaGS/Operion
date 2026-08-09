package com.operion.academic.api;

import java.util.List;

import com.operion.academic.AcademicService;
import com.operion.academic.SubjectRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subjects")
public class SubjectController {

	private final AcademicService academicService;
	private final SubjectRepository subjectRepository;

	public SubjectController(AcademicService academicService, SubjectRepository subjectRepository) {
		this.academicService = academicService;
		this.subjectRepository = subjectRepository;
	}

	@PostMapping
	public SubjectResponse create(@RequestBody CreateSubjectRequest request) {
		return SubjectResponse.from(academicService.createSubject(request.name(), request.code(), request.elective()));
	}

	@GetMapping
	public List<SubjectResponse> list() {
		return subjectRepository.findAll().stream().map(SubjectResponse::from).toList();
	}
}
