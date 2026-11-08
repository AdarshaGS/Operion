package com.operion.finance.api;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.authorization.RequirePermission;
import com.operion.finance.AdmissionFeeService;
import com.operion.finance.AdmissionFeeService.Preview;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only preview for the enrol dialog, so it can say what will be charged (or that nothing will)
 * before the registrar saves. Reachable by fee viewers and by enrolment managers, who may not hold FEE_VIEW.
 */
@RestController
@RequestMapping("/api/v1/admission-fees")
public class AdmissionFeeController {

	private final AdmissionFeeService admissionFeeService;
	private final AcademicYearRepository academicYearRepository;
	private final SectionRepository sectionRepository;

	public AdmissionFeeController(AdmissionFeeService admissionFeeService, AcademicYearRepository academicYearRepository,
			SectionRepository sectionRepository) {
		this.admissionFeeService = admissionFeeService;
		this.academicYearRepository = academicYearRepository;
		this.sectionRepository = sectionRepository;
	}

	@GetMapping("/preview")
	@RequirePermission(anyOf = { "FEE_VIEW", "STUDENT_ENROLLMENT_MANAGE" })
	public Preview preview(@RequestParam Long academicYearId, @RequestParam Long sectionId) {
		AcademicYear academicYear = academicYearRepository.findById(academicYearId)
				.orElseThrow(() -> new IllegalArgumentException("No academic year with id " + academicYearId));
		Section section = sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + sectionId));
		return admissionFeeService.preview(academicYear, section.getSchoolClass());
	}
}
