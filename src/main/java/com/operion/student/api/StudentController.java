package com.operion.student.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.student.Student;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/students")
@RequirePermission("STUDENT_VIEW")
public class StudentController {

	private final StudentService studentService;
	private final StudentRepository studentRepository;
	private final PersonRepository personRepository;

	public StudentController(StudentService studentService, StudentRepository studentRepository,
			PersonRepository personRepository) {
		this.studentService = studentService;
		this.studentRepository = studentRepository;
		this.personRepository = personRepository;
	}

	@PostMapping
	@RequirePermission("STUDENT_MANAGE")
	public StudentResponse admit(@RequestBody CreateStudentRequest request) {
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));

		Student student = studentService.admit(person, request.admissionNumber(), request.admissionDate(),
				request.admissionSource(), request.previousSchool(), request.tcNumber(), request.entranceScore(),
				request.bloodGroup(), request.category(), request.nationality(), request.remarks());
		return StudentResponse.from(student);
	}

	@GetMapping
	public List<StudentResponse> list() {
		return studentRepository.findAll().stream().map(StudentResponse::from).toList();
	}

	@GetMapping("/{studentId}")
	public StudentResponse get(@PathVariable Long studentId) {
		Student student = studentRepository.findById(studentId)
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + studentId));
		return StudentResponse.from(student);
	}
}
