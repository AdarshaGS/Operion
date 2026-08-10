package com.operion.academic.api;

import java.util.List;

import com.operion.academic.AcademicService;
import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SchoolClassStatus;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/school-classes")
public class SchoolClassController {

	private final AcademicService academicService;
	private final SchoolClassRepository schoolClassRepository;
	private final AcademicYearRepository academicYearRepository;
	private final CampusRepository campusRepository;
	private final GradeLevelRepository gradeLevelRepository;

	public SchoolClassController(AcademicService academicService, SchoolClassRepository schoolClassRepository,
			AcademicYearRepository academicYearRepository, CampusRepository campusRepository,
			GradeLevelRepository gradeLevelRepository) {
		this.academicService = academicService;
		this.schoolClassRepository = schoolClassRepository;
		this.academicYearRepository = academicYearRepository;
		this.campusRepository = campusRepository;
		this.gradeLevelRepository = gradeLevelRepository;
	}

	@PostMapping
	public SchoolClassResponse create(@RequestBody CreateSchoolClassRequest request) {
		AcademicYear academicYear = academicYearRepository.findById(request.academicYearId())
				.orElseThrow(() -> new IllegalArgumentException("No academic year with id " + request.academicYearId()));
		Campus campus = campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));
		GradeLevel gradeLevel = gradeLevelRepository.findById(request.gradeLevelId())
				.orElseThrow(() -> new IllegalArgumentException("No grade level with id " + request.gradeLevelId()));

		SchoolClass schoolClass = academicService.createSchoolClass(academicYear, campus, gradeLevel, request.displayName());
		return SchoolClassResponse.from(schoolClass);
	}

	@GetMapping
	public List<SchoolClassResponse> list(@RequestParam(required = false) Long academicYearId) {
		List<SchoolClass> schoolClasses = academicYearId != null
				? schoolClassRepository.findByAcademicYearId(academicYearId)
				: schoolClassRepository.findAll();
		return schoolClasses.stream().map(SchoolClassResponse::from).toList();
	}

	@PostMapping("/{id}/status")
	public SchoolClassResponse changeStatus(@PathVariable Long id, @RequestBody ChangeStatusRequest request) {
		SchoolClass schoolClass = schoolClassRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No school class with id " + id));
		return SchoolClassResponse.from(
				academicService.changeSchoolClassStatus(schoolClass, SchoolClassStatus.valueOf(request.status())));
	}
}
