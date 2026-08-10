package com.operion.academic.api;

import java.util.List;

import com.operion.academic.AcademicService;
import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.GradeLevelStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grade-levels")
public class GradeLevelController {

	private final AcademicService academicService;
	private final GradeLevelRepository gradeLevelRepository;

	public GradeLevelController(AcademicService academicService, GradeLevelRepository gradeLevelRepository) {
		this.academicService = academicService;
		this.gradeLevelRepository = gradeLevelRepository;
	}

	@PostMapping
	public GradeLevelResponse create(@RequestBody CreateGradeLevelRequest request) {
		return GradeLevelResponse.from(
				academicService.createGradeLevel(request.name(), request.sequenceOrder(), request.stage()));
	}

	@GetMapping
	public List<GradeLevelResponse> list() {
		return gradeLevelRepository.findAll().stream().map(GradeLevelResponse::from).toList();
	}

	@PostMapping("/{id}/status")
	public GradeLevelResponse changeStatus(@PathVariable Long id, @RequestBody ChangeStatusRequest request) {
		GradeLevel gradeLevel = gradeLevelRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No grade level with id " + id));
		return GradeLevelResponse.from(
				academicService.changeGradeLevelStatus(gradeLevel, GradeLevelStatus.valueOf(request.status())));
	}
}
