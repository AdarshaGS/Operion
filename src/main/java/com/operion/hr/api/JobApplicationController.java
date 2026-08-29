package com.operion.hr.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.hr.HrService;
import com.operion.hr.JobApplication;
import com.operion.hr.JobApplicationRepository;
import com.operion.hr.JobApplicationStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No class-level @RequirePermission - submit() must stay public (no annotation is the
 * "reachable by any authenticated org member" default per RequirePermission's javadoc,
 * but submit() goes further: it's whitelisted as fully unauthenticated in
 * JwtAuthenticationInterceptor.isPublic(), same shape as /api/v1/auth/claim-invite).
 * decidedBy on approve/reject always comes from TenantContext, never a request body
 * field - see TransferRequestController's identical note.
 */
@RestController
@RequestMapping("/api/v1/job-applications")
public class JobApplicationController {

	private final HrService hrService;
	private final JobApplicationRepository jobApplicationRepository;

	public JobApplicationController(HrService hrService, JobApplicationRepository jobApplicationRepository) {
		this.hrService = hrService;
		this.jobApplicationRepository = jobApplicationRepository;
	}

	@PostMapping
	public JobApplicationResponse submit(@RequestBody SubmitJobApplicationRequest request) {
		JobApplication jobApplication = hrService.submitJobApplication(
				request.organisationSlug(), request.applicantName(), request.email(), request.specialization(), request.yearsExperience());
		return JobApplicationResponse.from(jobApplication);
	}

	@GetMapping
	@RequirePermission("HR_RECRUITMENT_MANAGE")
	public List<JobApplicationResponse> list(@RequestParam(required = false) String status) {
		JobApplicationStatus parsedStatus = status != null ? JobApplicationStatus.valueOf(status) : null;
		List<JobApplication> applications = parsedStatus != null
				? jobApplicationRepository.findByStatus(parsedStatus)
				: jobApplicationRepository.findAll();
		return applications.stream().map(JobApplicationResponse::from).toList();
	}

	@PostMapping("/{id}/approve")
	@RequirePermission("HR_RECRUITMENT_MANAGE")
	public JobApplicationResponse approve(@PathVariable Long id) {
		return JobApplicationResponse.from(hrService.approveJobApplication(findJobApplication(id), TenantContext.getActorId()));
	}

	@PostMapping("/{id}/reject")
	@RequirePermission("HR_RECRUITMENT_MANAGE")
	public JobApplicationResponse reject(@PathVariable Long id) {
		return JobApplicationResponse.from(hrService.rejectJobApplication(findJobApplication(id), TenantContext.getActorId()));
	}

	private JobApplication findJobApplication(Long id) {
		return jobApplicationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No job application with id " + id));
	}
}
