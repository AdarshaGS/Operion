package com.operion.organisation.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.AcademicYearService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creation starts a year DRAFT and not current (per the AcademicYear constructor). Create, marking current and
 * closing all go through AcademicYearService, which enforces the date-range and one-current-at-a-time invariants. */
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
	public AcademicYearResponse create(@Valid @RequestBody CreateAcademicYearRequest request) {
		return AcademicYearResponse.from(academicYearService.create(request.name(), request.startDate(), request.endDate()));
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
