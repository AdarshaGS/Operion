package com.operion.student.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.student.StudentApplication;
import com.operion.student.StudentApplicationRepository;
import com.operion.student.StudentApplicationService;
import com.operion.student.StudentApplicationStatus;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Unlike JobApplicationController's public submit() (a real careers-page candidate),
 * every endpoint here sits behind STUDENT_APPLICATION_MANAGE - this pipeline is the
 * admissions desk recording a walk-in/phone inquiry, not a public-facing form. */
@RestController
@RequestMapping("/api/v1/student-applications")
@RequirePermission("STUDENT_APPLICATION_MANAGE")
public class StudentApplicationController {

	private final StudentApplicationService studentApplicationService;
	private final StudentApplicationRepository studentApplicationRepository;

	public StudentApplicationController(StudentApplicationService studentApplicationService,
			StudentApplicationRepository studentApplicationRepository) {
		this.studentApplicationService = studentApplicationService;
		this.studentApplicationRepository = studentApplicationRepository;
	}

	@PostMapping
	public StudentApplicationResponse submit(@Valid @RequestBody SubmitStudentApplicationRequest request) {
		StudentApplication application = studentApplicationService.submit(request.applicantName(), request.dateOfBirth(),
				request.gender(), request.guardianName(), request.guardianPhone(), request.desiredGradeLevelId(), request.notes());
		return StudentApplicationResponse.from(application);
	}

	@GetMapping
	public List<StudentApplicationResponse> list(@RequestParam(required = false) String status) {
		StudentApplicationStatus parsedStatus = status != null ? StudentApplicationStatus.valueOf(status) : null;
		List<StudentApplication> applications = parsedStatus != null
				? studentApplicationRepository.findByStatus(parsedStatus)
				: studentApplicationRepository.findAll();
		return applications.stream().map(StudentApplicationResponse::from).toList();
	}

	@PostMapping("/{id}/approve")
	public StudentApplicationResponse approve(@PathVariable Long id) {
		return StudentApplicationResponse.from(studentApplicationService.approve(find(id), TenantContext.getActorId()));
	}

	@PostMapping("/{id}/reject")
	public StudentApplicationResponse reject(@PathVariable Long id) {
		return StudentApplicationResponse.from(studentApplicationService.reject(find(id), TenantContext.getActorId()));
	}

	private StudentApplication find(Long id) {
		return studentApplicationRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No student application with id " + id));
	}
}
