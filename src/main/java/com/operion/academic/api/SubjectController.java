package com.operion.academic.api;

import java.util.List;

import com.operion.academic.AcademicService;
import com.operion.academic.Subject;
import com.operion.academic.SubjectImportService;
import com.operion.academic.SubjectRepository;
import com.operion.academic.SubjectStatus;
import com.operion.authorization.RequirePermission;
import com.operion.common.imports.ImportRowResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/subjects")
@RequirePermission("SUBJECT_VIEW")
public class SubjectController {

	private final AcademicService academicService;
	private final SubjectRepository subjectRepository;
	private final SubjectImportService subjectImportService;

	public SubjectController(
			AcademicService academicService, SubjectRepository subjectRepository, SubjectImportService subjectImportService) {
		this.academicService = academicService;
		this.subjectRepository = subjectRepository;
		this.subjectImportService = subjectImportService;
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

	/** Bulk CSV/Excel import - reuses the same createSubject path as create() above, one
	 * row at a time; see SubjectImportService/SubjectRowImportService for the per-row
	 * transaction isolation that makes a partial import safe. */
	@PostMapping("/import")
	@RequirePermission("SUBJECT_MANAGE")
	public List<ImportRowResult> importFile(@RequestParam("file") MultipartFile file,
			@RequestParam(name = "validateOnly", defaultValue = "false") boolean validateOnly) {
		return subjectImportService.importFile(file, validateOnly);
	}

	/** Inherits this controller's class-level SUBJECT_VIEW gate. */
	@GetMapping("/export")
	public List<SubjectResponse> export() {
		return subjectRepository.findAll().stream().map(SubjectResponse::from).toList();
	}
}
