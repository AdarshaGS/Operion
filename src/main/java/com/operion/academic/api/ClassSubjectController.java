package com.operion.academic.api;

import java.util.List;

import com.operion.academic.AcademicService;
import com.operion.academic.ClassSubject;
import com.operion.academic.ClassSubjectRepository;
import com.operion.academic.ClassSubjectStatus;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/school-classes/{classId}/subjects")
public class ClassSubjectController {

	private final AcademicService academicService;
	private final ClassSubjectRepository classSubjectRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final SubjectRepository subjectRepository;

	public ClassSubjectController(AcademicService academicService, ClassSubjectRepository classSubjectRepository,
			SchoolClassRepository schoolClassRepository, SubjectRepository subjectRepository) {
		this.academicService = academicService;
		this.classSubjectRepository = classSubjectRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.subjectRepository = subjectRepository;
	}

	@PostMapping
	public ClassSubjectResponse assign(@PathVariable Long classId, @RequestBody AssignSubjectRequest request) {
		SchoolClass schoolClass = schoolClassRepository.findById(classId)
				.orElseThrow(() -> new IllegalArgumentException("No school class with id " + classId));
		Subject subject = subjectRepository.findById(request.subjectId())
				.orElseThrow(() -> new IllegalArgumentException("No subject with id " + request.subjectId()));

		return ClassSubjectResponse.from(academicService.assignSubjectToClass(schoolClass, subject, request.mandatory()));
	}

	@GetMapping
	public List<ClassSubjectResponse> list(@PathVariable Long classId) {
		return classSubjectRepository.findBySchoolClassId(classId).stream().map(ClassSubjectResponse::from).toList();
	}

	@PostMapping("/{id}/status")
	public ClassSubjectResponse changeStatus(
			@PathVariable Long classId, @PathVariable Long id, @RequestBody ChangeStatusRequest request) {
		ClassSubject classSubject = classSubjectRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No class subject with id " + id));
		return ClassSubjectResponse.from(
				academicService.changeClassSubjectStatus(classSubject, ClassSubjectStatus.valueOf(request.status())));
	}
}
