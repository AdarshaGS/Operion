package com.operion.organisation.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.AcademicYearService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creation is "save the row" (starts DRAFT, not current, per the AcademicYear
 * constructor) - no service needed for that path. Marking current/closing goes through
 * AcademicYearService, which enforces the one-current-at-a-time invariant. */
@RestController
@RequestMapping("/api/v1/academic-years")
public class AcademicYearController {

	private final AcademicYearRepository academicYearRepository;
	private final AcademicYearService academicYearService;

	public AcademicYearController(AcademicYearRepository academicYearRepository, AcademicYearService academicYearService) {
		this.academicYearRepository = academicYearRepository;
		this.academicYearService = academicYearService;
	}

	@PostMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public AcademicYearResponse create(@RequestBody CreateAcademicYearRequest request) {
		AcademicYear academicYear = new AcademicYear(request.name(), request.startDate(), request.endDate());
		return AcademicYearResponse.from(academicYearRepository.save(academicYear));
	}

	@GetMapping
	public List<AcademicYearResponse> list() {
		return academicYearRepository.findAll().stream().map(AcademicYearResponse::from).toList();
	}

	@PostMapping("/{id}/mark-current")
	@RequirePermission("ORGANISATION_MANAGE")
	public AcademicYearResponse markCurrent(@PathVariable Long id) {
		return AcademicYearResponse.from(academicYearService.markCurrent(id));
	}

	@PostMapping("/{id}/close")
	@RequirePermission("ORGANISATION_MANAGE")
	public AcademicYearResponse close(@PathVariable Long id) {
		return AcademicYearResponse.from(academicYearService.close(id));
	}
}
