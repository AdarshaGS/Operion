package com.operion.academic.api;

import java.util.List;

import com.operion.academic.AcademicService;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SectionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/school-classes/{classId}/sections")
public class SectionController {

	private final AcademicService academicService;
	private final SectionRepository sectionRepository;
	private final SchoolClassRepository schoolClassRepository;

	public SectionController(AcademicService academicService, SectionRepository sectionRepository,
			SchoolClassRepository schoolClassRepository) {
		this.academicService = academicService;
		this.sectionRepository = sectionRepository;
		this.schoolClassRepository = schoolClassRepository;
	}

	@PostMapping
	public SectionResponse create(@PathVariable Long classId, @RequestBody CreateSectionRequest request) {
		SchoolClass schoolClass = findClassOrThrow(classId);
		return SectionResponse.from(
				academicService.createSection(schoolClass, request.name(), request.capacity(), request.room()));
	}

	@GetMapping
	public List<SectionResponse> list(@PathVariable Long classId) {
		return sectionRepository.findBySchoolClassId(classId).stream().map(SectionResponse::from).toList();
	}

	private SchoolClass findClassOrThrow(Long classId) {
		return schoolClassRepository.findById(classId)
				.orElseThrow(() -> new IllegalArgumentException("No school class with id " + classId));
	}
}
