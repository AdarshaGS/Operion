package com.operion.student.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.student.Student;
import com.operion.student.StudentExit;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentExitType;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/students/{studentId}/exits")
@RequirePermission("STUDENT_VIEW")
public class StudentExitController {

	private final StudentService studentService;
	private final StudentRepository studentRepository;
	private final StudentExitRepository studentExitRepository;

	public StudentExitController(
			StudentService studentService, StudentRepository studentRepository, StudentExitRepository studentExitRepository) {
		this.studentService = studentService;
		this.studentRepository = studentRepository;
		this.studentExitRepository = studentExitRepository;
	}

	@PostMapping
	@RequirePermission("STUDENT_EXIT_MANAGE")
	public StudentExitResponse recordExit(@PathVariable Long studentId, @RequestBody RecordExitRequest request) {
		Student student = studentRepository.findById(studentId)
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + studentId));

		StudentExit exit = studentService.recordExit(student, StudentExitType.valueOf(request.exitType()),
				request.exitDate(), request.reason(), request.destinationSchool(), request.initiatedBy());
		return StudentExitResponse.from(exit);
	}

	@GetMapping
	public List<StudentExitResponse> list(@PathVariable Long studentId) {
		return studentExitRepository.findByStudentId(studentId).stream().map(StudentExitResponse::from).toList();
	}
}
