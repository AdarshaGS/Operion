package com.operion.hr.api;

import com.operion.organisation.DesignationRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.Department;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Campus;
import com.operion.hr.JobApplicationHireService;
import com.operion.hr.EmploymentType;
import com.operion.common.InvalidRequestException;
import com.operion.common.EnumParam;
import java.util.List;

import com.operion.academic.SubjectRepository;
import com.operion.academic.SubjectStatus;
import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.hr.HrService;
import com.operion.hr.JobApplication;
import com.operion.hr.JobApplicationRepository;
import com.operion.hr.JobApplicationStatus;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
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
 * field - see TransferRequestController's identical note. subjectsForOrganisation() is
 * whitelisted the same way (#286) - the public form needs the hiring org's subject catalog
 * to populate its specialization dropdown, same trust tier as submit() itself, and returns
 * only id/name (PublicSubjectResponse), not the full authenticated SubjectResponse shape.
 */
@RestController
@RequestMapping("/api/v1/job-applications")
public class JobApplicationController {

	private final HrService hrService;
	private final JobApplicationRepository jobApplicationRepository;
	private final OrganisationRepository organisationRepository;
	private final SubjectRepository subjectRepository;
	private final JobApplicationHireService hireService;
	private final DesignationRepository designationRepository;
	private final CampusRepository campusRepository;
	private final DepartmentRepository departmentRepository;

	public JobApplicationController(HrService hrService, JobApplicationRepository jobApplicationRepository,
			OrganisationRepository organisationRepository, SubjectRepository subjectRepository,
			JobApplicationHireService hireService, DesignationRepository designationRepository,
			CampusRepository campusRepository, DepartmentRepository departmentRepository) {
		this.hireService = hireService;
		this.designationRepository = designationRepository;
		this.campusRepository = campusRepository;
		this.departmentRepository = departmentRepository;
		this.hrService = hrService;
		this.jobApplicationRepository = jobApplicationRepository;
		this.organisationRepository = organisationRepository;
		this.subjectRepository = subjectRepository;
	}

	@PostMapping
	public JobApplicationResponse submit(@RequestBody SubmitJobApplicationRequest request) {
		JobApplication jobApplication = hrService.submitJobApplication(
				request.organisationSlug(), request.applicantName(), request.email(), request.specialization(), request.yearsExperience());
		return JobApplicationResponse.from(jobApplication);
	}

	@GetMapping("/subjects")
	public List<PublicSubjectResponse> subjectsForOrganisation(@RequestParam String organisationSlug) {
		Organisation organisation = organisationRepository.findBySlug(organisationSlug)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with slug " + organisationSlug));
		TenantContext.set(organisation.getId(), null);
		return subjectRepository.findByStatus(SubjectStatus.ACTIVE).stream().map(PublicSubjectResponse::from).toList();
	}

	@GetMapping
	@RequirePermission("HR_RECRUITMENT_MANAGE")
	public List<JobApplicationResponse> list(@RequestParam(required = false) String status) {
		JobApplicationStatus parsedStatus = status != null ? EnumParam.parse(JobApplicationStatus.class, status) : null;
		List<JobApplication> applications = parsedStatus != null
				? jobApplicationRepository.findByStatus(parsedStatus)
				: jobApplicationRepository.findAll();
		return applications.stream().map(JobApplicationResponse::from).toList();
	}

	@PostMapping("/{id}/approve")
	@RequirePermission("HR_RECRUITMENT_MANAGE")
	public JobApplicationResponse approve(@PathVariable Long id, @RequestBody(required = false) ApproveJobApplicationRequest request) {
		JobApplication application = findJobApplication(id);
		if (request == null || !request.hasHireDetails()) {
			return JobApplicationResponse.from(hrService.approveJobApplication(application, TenantContext.getActorId()));
		}
		EmploymentType employmentType = EnumParam.parse(EmploymentType.class, request.employmentType());
		if (request.designationId() == null || request.dateOfJoining() == null
				|| request.employeeCode() == null || request.employeeCode().isBlank()) {
			throw new InvalidRequestException("designationId, employmentType, dateOfJoining and employeeCode are required to hire the applicant");
		}
		Designation designation = designationRepository.findById(request.designationId())
				.orElseThrow(() -> new IllegalArgumentException("No designation with id " + request.designationId()));
		Campus campus = request.campusId() == null ? null : campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));
		Department department = request.departmentId() == null ? null : departmentRepository.findById(request.departmentId())
				.orElseThrow(() -> new IllegalArgumentException("No department with id " + request.departmentId()));
		hireService.approveAndHire(application, TenantContext.getActorId(), campus, designation, department,
				request.employeeCode(), request.dateOfJoining(), employmentType);
		return JobApplicationResponse.from(findJobApplication(id));
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
