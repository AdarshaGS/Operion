package com.operion.student.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.student.Student;
import com.operion.student.StudentImportService;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/students")
@RequirePermission("STUDENT_VIEW")
public class StudentController {

	private final StudentService studentService;
	private final StudentRepository studentRepository;
	private final PersonRepository personRepository;
	private final StudentImportService studentImportService;

	public StudentController(StudentService studentService, StudentRepository studentRepository,
			PersonRepository personRepository, StudentImportService studentImportService) {
		this.studentService = studentService;
		this.studentRepository = studentRepository;
		this.personRepository = personRepository;
		this.studentImportService = studentImportService;
	}

	@PostMapping
	@RequirePermission("STUDENT_MANAGE")
	public StudentResponse admit(@Valid @RequestBody CreateStudentRequest request) {
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));

		Student student = studentService.admit(person, request.admissionNumber(), request.admissionDate(),
				request.admissionSource(), request.previousSchool(), request.tcNumber(), request.entranceScore(),
				request.bloodGroup(), request.category(), request.nationality(), request.remarks(),
				request.medicalAlerts(), request.emergencyContactName(), request.emergencyContactPhone());
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

	/** Bulk CSV admission (#28) - reuses the same Person+Student write path as admit()
	 * above, one row at a time; see StudentImportService/StudentRowImportService for the
	 * per-row transaction isolation that makes a partial import safe. */
	@PostMapping("/import")
	@RequirePermission("STUDENT_MANAGE")
	public List<StudentImportRowResult> importCsv(@RequestParam("file") MultipartFile file) {
		return studentImportService.importCsv(file);
	}

	/** Inherits this controller's class-level STUDENT_VIEW gate (#147's "permission-
	 * gated export path", reusing RequirePermission rather than adding a new permission). */
	@GetMapping("/export")
	public List<StudentExportResponse> export() {
		return studentRepository.findAll().stream().map(StudentExportResponse::from).toList();
	}
}
