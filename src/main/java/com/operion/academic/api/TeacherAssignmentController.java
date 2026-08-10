package com.operion.academic.api;

import java.util.List;

import com.operion.academic.AcademicService;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentType;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeacherAssignmentController {

	private final AcademicService academicService;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final SectionRepository sectionRepository;
	private final SubjectRepository subjectRepository;
	private final PersonRepository personRepository;

	public TeacherAssignmentController(AcademicService academicService,
			TeacherAssignmentRepository teacherAssignmentRepository, SectionRepository sectionRepository,
			SubjectRepository subjectRepository, PersonRepository personRepository) {
		this.academicService = academicService;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
		this.sectionRepository = sectionRepository;
		this.subjectRepository = subjectRepository;
		this.personRepository = personRepository;
	}

	@PostMapping("/api/v1/sections/{sectionId}/teacher-assignments")
	public TeacherAssignmentResponse assign(@PathVariable Long sectionId, @RequestBody AssignTeacherRequest request) {
		Section section = sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + sectionId));
		Subject subject = request.subjectId() != null
				? subjectRepository.findById(request.subjectId())
						.orElseThrow(() -> new IllegalArgumentException("No subject with id " + request.subjectId()))
				: null;
		Person teacherPerson = personRepository.findById(request.teacherPersonId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.teacherPersonId()));
		TeacherAssignmentType assignmentType = TeacherAssignmentType.valueOf(request.assignmentType());

		return TeacherAssignmentResponse.from(
				academicService.assignTeacher(section, subject, teacherPerson, assignmentType, request.startDate()));
	}

	@GetMapping("/api/v1/sections/{sectionId}/teacher-assignments")
	public List<TeacherAssignmentResponse> listForSection(@PathVariable Long sectionId) {
		return teacherAssignmentRepository.findBySectionId(sectionId).stream().map(TeacherAssignmentResponse::from).toList();
	}

	@GetMapping("/api/v1/teachers/{personId}/assignments")
	public List<TeacherAssignmentResponse> listForTeacher(@PathVariable Long personId) {
		return teacherAssignmentRepository.findByTeacherPersonId(personId).stream()
				.map(TeacherAssignmentResponse::from)
				.toList();
	}

	@PostMapping("/api/v1/teacher-assignments/{id}/end")
	public TeacherAssignmentResponse end(@PathVariable Long id, @RequestBody EndTeacherAssignmentRequest request) {
		TeacherAssignment assignment = teacherAssignmentRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No teacher assignment with id " + id));
		return TeacherAssignmentResponse.from(academicService.endTeacherAssignment(assignment, request.endDate()));
	}
}
