package com.operion.academic.api;

import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SectionRepository;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stricter than DashboardController's own "academic year + at least one class exist"
 * checklist signal - this one also requires a Section, since that's what admission
 * actually needs to enroll into (#111). Exposed ungated (any authenticated org member)
 * so screens outside ORGANISATION_MANAGE - like student admission - can gate on it
 * without needing dashboard access themselves.
 */
@RestController
@RequestMapping("/api/v1/academics/setup-status")
public class AcademicSetupStatusController {

	private final AcademicYearRepository academicYearRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final SectionRepository sectionRepository;

	public AcademicSetupStatusController(AcademicYearRepository academicYearRepository, SchoolClassRepository schoolClassRepository,
			SectionRepository sectionRepository) {
		this.academicYearRepository = academicYearRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.sectionRepository = sectionRepository;
	}

	@GetMapping
	public AcademicSetupStatusResponse get() {
		boolean configured = academicYearRepository.existsByCurrentTrue() && schoolClassRepository.count() > 0
				&& sectionRepository.count() > 0;
		return new AcademicSetupStatusResponse(configured);
	}
}
