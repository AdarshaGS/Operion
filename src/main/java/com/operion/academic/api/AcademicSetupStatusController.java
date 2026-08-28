package com.operion.academic.api;

import com.operion.academic.SchoolClassRepository;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same "academic year + at least one class exist" signal DashboardController computes for
 * the setup checklist, exposed ungated (any authenticated org member) so screens outside
 * ORGANISATION_MANAGE - like student admission - can gate on it without needing dashboard
 * access themselves (#111-family admission-flow work).
 */
@RestController
@RequestMapping("/api/v1/academics/setup-status")
public class AcademicSetupStatusController {

	private final AcademicYearRepository academicYearRepository;
	private final SchoolClassRepository schoolClassRepository;

	public AcademicSetupStatusController(AcademicYearRepository academicYearRepository, SchoolClassRepository schoolClassRepository) {
		this.academicYearRepository = academicYearRepository;
		this.schoolClassRepository = schoolClassRepository;
	}

	@GetMapping
	public AcademicSetupStatusResponse get() {
		boolean configured = academicYearRepository.existsByCurrentTrue() && schoolClassRepository.count() > 0;
		return new AcademicSetupStatusResponse(configured);
	}
}
