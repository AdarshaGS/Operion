package com.operion.finance.api;

import com.operion.authorization.RequirePermission;
import com.operion.finance.AdmissionFeeClearanceService;
import com.operion.finance.AdmissionFeeService;
import com.operion.finance.AdmissionFeeService.FeeClearanceView;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One enrollment's admission-fee standing with the invoices behind it. Reading carries amounts, so it needs FEE_VIEW.
 * Overriding and revoking an override need FEE_ADMISSION_CLEARANCE_OVERRIDE instead (a method-level permission
 * replaces the class-level one), and answer with the refreshed view.
 */
@RestController
@RequestMapping("/api/v1/students/{studentId}/enrollments/{enrollmentId}/fee-clearance")
@RequirePermission("FEE_VIEW")
public class FeeClearanceController {

	private final AdmissionFeeService admissionFeeService;
	private final AdmissionFeeClearanceService clearanceService;
	private final StudentEnrollmentRepository studentEnrollmentRepository;

	public FeeClearanceController(AdmissionFeeService admissionFeeService, AdmissionFeeClearanceService clearanceService,
			StudentEnrollmentRepository studentEnrollmentRepository) {
		this.admissionFeeService = admissionFeeService;
		this.clearanceService = clearanceService;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
	}

	@GetMapping
	public FeeClearanceView get(@PathVariable Long studentId, @PathVariable Long enrollmentId) {
		return admissionFeeService.describe(enrollmentOf(studentId, enrollmentId));
	}

	/** PENDING -> EXEMPTED. A reason is mandatory; 409 when the enrollment is not PENDING. */
	@PostMapping("/override")
	@RequirePermission("FEE_ADMISSION_CLEARANCE_OVERRIDE")
	public FeeClearanceView override(@PathVariable Long studentId, @PathVariable Long enrollmentId,
			@Valid @RequestBody OverrideFeeClearanceRequest request) {
		enrollmentOf(studentId, enrollmentId);
		return admissionFeeService.describe(clearanceService.override(enrollmentId, request.reason()));
	}

	/** EXEMPTED -> CLEARED or PENDING, recalculated from the admission invoices; 409 when the enrollment is not EXEMPTED. */
	@DeleteMapping("/override")
	@RequirePermission("FEE_ADMISSION_CLEARANCE_OVERRIDE")
	public FeeClearanceView revokeOverride(@PathVariable Long studentId, @PathVariable Long enrollmentId) {
		enrollmentOf(studentId, enrollmentId);
		return admissionFeeService.describe(clearanceService.revokeOverride(enrollmentId));
	}

	private StudentEnrollment enrollmentOf(Long studentId, Long enrollmentId) {
		return studentEnrollmentRepository.findById(enrollmentId)
				.filter(e -> e.getStudent().getId().equals(studentId))
				.orElseThrow(() -> new IllegalArgumentException("No enrollment with id " + enrollmentId + " for student " + studentId));
	}
}
