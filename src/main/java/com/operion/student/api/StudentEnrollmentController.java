package com.operion.student.api;

import java.util.List;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.authorization.RequirePermission;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/students/{studentId}/enrollments")
@RequirePermission("STUDENT_VIEW")
public class StudentEnrollmentController {

	private final StudentService studentService;
	private final StudentRepository studentRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final AcademicYearRepository academicYearRepository;
	private final SectionRepository sectionRepository;

	public StudentEnrollmentController(StudentService studentService, StudentRepository studentRepository,
			StudentEnrollmentRepository studentEnrollmentRepository, AcademicYearRepository academicYearRepository,
			SectionRepository sectionRepository) {
		this.studentService = studentService;
		this.studentRepository = studentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.academicYearRepository = academicYearRepository;
		this.sectionRepository = sectionRepository;
	}

	@PostMapping
	@RequirePermission("STUDENT_ENROLLMENT_MANAGE")
	public StudentEnrollmentResponse enroll(@PathVariable Long studentId, @RequestBody EnrollStudentRequest request) {
		Student student = findStudent(studentId);
		AcademicYear academicYear = findAcademicYear(request.academicYearId());
		Section section = findSection(request.sectionId());

		StudentEnrollment enrollment =
				studentService.enroll(student, academicYear, section, request.rollNumber(), request.enrolledDate());
		return StudentEnrollmentResponse.from(enrollment);
	}

	@PostMapping("/promote")
	@RequirePermission("STUDENT_ENROLLMENT_MANAGE")
	public StudentEnrollmentResponse promote(@PathVariable Long studentId, @RequestBody PromoteStudentRequest request) {
		Student student = findStudent(studentId);
		AcademicYear academicYear = findAcademicYear(request.academicYearId());
		Section section = findSection(request.sectionId());

		StudentEnrollment enrollment = studentService.promote(
				student, academicYear, section, request.rollNumber(), request.promotionDate(), request.repeated());
		return StudentEnrollmentResponse.from(enrollment);
	}

	@PostMapping("/reassign-section")
	@RequirePermission("STUDENT_ENROLLMENT_MANAGE")
	public StudentEnrollmentResponse reassignSection(
			@PathVariable Long studentId, @RequestBody ReassignSectionRequest request) {
		Student student = findStudent(studentId);
		Section section = findSection(request.sectionId());

		return StudentEnrollmentResponse.from(studentService.reassignSection(student, section));
	}

	@GetMapping
	public List<StudentEnrollmentResponse> list(@PathVariable Long studentId) {
		return studentEnrollmentRepository.findByStudentId(studentId).stream()
				.map(StudentEnrollmentResponse::from)
				.toList();
	}

	private Student findStudent(Long studentId) {
		return studentRepository.findById(studentId)
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + studentId));
	}

	private AcademicYear findAcademicYear(Long academicYearId) {
		return academicYearRepository.findById(academicYearId)
				.orElseThrow(() -> new IllegalArgumentException("No academic year with id " + academicYearId));
	}

	private Section findSection(Long sectionId) {
		return sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + sectionId));
	}
}
