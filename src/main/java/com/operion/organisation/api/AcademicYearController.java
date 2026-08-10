package com.operion.organisation.api;

import java.util.List;

import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No dedicated service - creation is "save the row" (starts DRAFT, not current, per the
 * AcademicYear constructor). Marking a year current/closing it is a real lifecycle
 * action with a one-current-at-a-time invariant to enforce - deliberately not built
 * here since nothing in the Academics module being wired up right now needs it yet;
 * SchoolClass creation only needs an academic year to exist and be pickable. */
@RestController
@RequestMapping("/api/v1/academic-years")
public class AcademicYearController {

	private final AcademicYearRepository academicYearRepository;

	public AcademicYearController(AcademicYearRepository academicYearRepository) {
		this.academicYearRepository = academicYearRepository;
	}

	@PostMapping
	public AcademicYearResponse create(@RequestBody CreateAcademicYearRequest request) {
		AcademicYear academicYear = new AcademicYear(request.name(), request.startDate(), request.endDate());
		return AcademicYearResponse.from(academicYearRepository.save(academicYear));
	}

	@GetMapping
	public List<AcademicYearResponse> list() {
		return academicYearRepository.findAll().stream().map(AcademicYearResponse::from).toList();
	}
}
