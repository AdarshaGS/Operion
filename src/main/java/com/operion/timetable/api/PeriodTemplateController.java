package com.operion.timetable.api;

import java.util.List;

import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.authorization.RequirePermission;
import com.operion.timetable.PeriodTemplate;
import com.operion.timetable.PeriodTemplateRepository;
import com.operion.timetable.TimetableService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Alternative school days (period templates) and which class follows which. The default day has no template. */
@RestController
@RequirePermission("TIMETABLE_VIEW")
public class PeriodTemplateController {

	private final TimetableService timetableService;
	private final PeriodTemplateRepository templateRepository;
	private final SchoolClassRepository classRepository;

	public PeriodTemplateController(TimetableService timetableService, PeriodTemplateRepository templateRepository,
			SchoolClassRepository classRepository) {
		this.timetableService = timetableService;
		this.templateRepository = templateRepository;
		this.classRepository = classRepository;
	}

	@GetMapping("/api/v1/timetable/templates")
	public List<PeriodTemplateResponse> list() {
		return timetableService.listTemplates().stream().map(this::response).toList();
	}

	@PostMapping("/api/v1/timetable/templates")
	@RequirePermission("TIMETABLE_MANAGE")
	public PeriodTemplateResponse create(@Valid @RequestBody PeriodTemplateRequest request) {
		return response(timetableService.createTemplate(request.name()));
	}

	@PutMapping("/api/v1/timetable/templates/{id}")
	@RequirePermission("TIMETABLE_MANAGE")
	public PeriodTemplateResponse rename(@PathVariable Long id, @Valid @RequestBody PeriodTemplateRequest request) {
		return response(timetableService.renameTemplate(find(id), request.name()));
	}

	@DeleteMapping("/api/v1/timetable/templates/{id}")
	@RequirePermission("TIMETABLE_MANAGE")
	public void delete(@PathVariable Long id) {
		timetableService.deleteTemplate(find(id));
	}

	@GetMapping("/api/v1/school-classes/{classId}/period-template")
	public ClassPeriodTemplateResponse ofClass(@PathVariable Long classId) {
		return classResponse(findClass(classId));
	}

	@PutMapping("/api/v1/school-classes/{classId}/period-template")
	@RequirePermission("TIMETABLE_MANAGE")
	public ClassPeriodTemplateResponse assign(@PathVariable Long classId, @RequestBody ClassPeriodTemplateRequest request) {
		SchoolClass schoolClass = findClass(classId);
		timetableService.assignTemplate(schoolClass, request.templateId() == null ? null : find(request.templateId()));
		return classResponse(schoolClass);
	}

	private ClassPeriodTemplateResponse classResponse(SchoolClass schoolClass) {
		var template = timetableService.templateOf(schoolClass);
		return new ClassPeriodTemplateResponse(schoolClass.getId(), template.map(PeriodTemplate::getId).orElse(null),
				template.map(PeriodTemplate::getName).orElse(null));
	}

	private PeriodTemplateResponse response(PeriodTemplate template) {
		return PeriodTemplateResponse.from(template, timetableService.listPeriods(template).size(), timetableService.classesUsing(template));
	}

	private PeriodTemplate find(Long id) {
		return templateRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No period template with id " + id));
	}

	private SchoolClass findClass(Long id) {
		return classRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No class with id " + id));
	}
}
