package com.operion.academic.api;

import java.util.List;

import com.operion.academic.AcademicService;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SectionStatus;
import com.operion.authorization.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No dedicated *_VIEW/*_MANAGE codes exist for Section specifically - it's a class-scoped
 * sub-resource, so it reuses CLASS_VIEW/CLASS_MANAGE (same reasoning as ClassSubject). */
@RestController
@RequestMapping("/api/v1/school-classes/{classId}/sections")
@RequirePermission("CLASS_VIEW")
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
	@RequirePermission("CLASS_MANAGE")
	public SectionResponse create(@PathVariable Long classId, @RequestBody CreateSectionRequest request) {
		SchoolClass schoolClass = findClassOrThrow(classId);
		return SectionResponse.from(
				academicService.createSection(schoolClass, request.name(), request.capacity(), request.room()));
	}

	@GetMapping
	public List<SectionResponse> list(@PathVariable Long classId) {
		return sectionRepository.findBySchoolClassId(classId).stream().map(SectionResponse::from).toList();
	}

	@PostMapping("/{sectionId}/status")
	@RequirePermission("CLASS_MANAGE")
	public SectionResponse changeStatus(
			@PathVariable Long classId, @PathVariable Long sectionId, @RequestBody ChangeStatusRequest request) {
		Section section = sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + sectionId));
		return SectionResponse.from(academicService.changeSectionStatus(section, SectionStatus.valueOf(request.status())));
	}

	@PatchMapping("/{sectionId}")
	@RequirePermission("CLASS_MANAGE")
	public SectionResponse update(
			@PathVariable Long classId, @PathVariable Long sectionId, @RequestBody UpdateSectionRequest request) {
		Section section = sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + sectionId));
		return SectionResponse.from(academicService.updateSection(section, request.name(), request.capacity(), request.room()));
	}

	private SchoolClass findClassOrThrow(Long classId) {
		return schoolClassRepository.findById(classId)
				.orElseThrow(() -> new IllegalArgumentException("No school class with id " + classId));
	}
}
