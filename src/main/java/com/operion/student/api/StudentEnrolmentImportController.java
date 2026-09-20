package com.operion.student.api;

import java.util.List;

import com.operion.academic.StudentEnrolmentImportService;
import com.operion.authorization.RequirePermission;
import com.operion.common.imports.ImportRowResult;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk import/export isn't scoped to one student, unlike StudentEnrollmentController's
 * nested /students/{studentId}/enrollments - so this is a separate top-level pair of
 * endpoints instead of bolting onto that controller.
 */
@RestController
@RequestMapping("/api/v1/student-enrollments")
public class StudentEnrolmentImportController {

	private final StudentEnrolmentImportService studentEnrolmentImportService;
	private final StudentEnrollmentRepository studentEnrollmentRepository;

	public StudentEnrolmentImportController(StudentEnrolmentImportService studentEnrolmentImportService,
			StudentEnrollmentRepository studentEnrollmentRepository) {
		this.studentEnrolmentImportService = studentEnrolmentImportService;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
	}

	@PostMapping("/import")
	@RequirePermission("STUDENT_ENROLLMENT_MANAGE")
	public List<ImportRowResult> importFile(@RequestParam("file") MultipartFile file,
			@RequestParam(name = "validateOnly", defaultValue = "false") boolean validateOnly) {
		return studentEnrolmentImportService.importFile(file, validateOnly);
	}

	@GetMapping("/export")
	@RequirePermission("STUDENT_VIEW")
	public List<StudentEnrolmentExportResponse> export() {
		return studentEnrollmentRepository.findAll().stream().map(StudentEnrolmentExportResponse::from).toList();
	}
}
