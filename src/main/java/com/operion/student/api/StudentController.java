package com.operion.student.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.operion.authorization.RequirePermission;
import com.operion.common.api.PageResponse;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.parent.StudentGuardian;
import com.operion.parent.StudentGuardianRepository;
import com.operion.parent.StudentGuardianStatus;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentImportService;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import com.operion.student.StudentStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final StudentGuardianRepository studentGuardianRepository;

	public StudentController(StudentService studentService, StudentRepository studentRepository,
			PersonRepository personRepository, StudentImportService studentImportService,
			StudentEnrollmentRepository studentEnrollmentRepository, StudentGuardianRepository studentGuardianRepository) {
		this.studentService = studentService;
		this.studentRepository = studentRepository;
		this.personRepository = personRepository;
		this.studentImportService = studentImportService;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.studentGuardianRepository = studentGuardianRepository;
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

	/** Separate from list() above - that endpoint's unpaginated "give me everything" shape
	 * is relied on by lookup maps all over the app (attendance, fees, exams, transport...),
	 * so it stays as-is; this is purpose-built for the student list screen's search/filter/
	 * pagination needs (#245), enriched with columns (current section, primary guardian)
	 * that live on other entities entirely. */
	@GetMapping("/search")
	public PageResponse<StudentListRowResponse> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) String status, @RequestParam(required = false) Long schoolClassId,
			@RequestParam(required = false) Long sectionId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate admissionDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate admissionDateTo,
			@PageableDefault(size = 25, sort = "admissionDate", direction = Sort.Direction.DESC) Pageable pageable) {
		StudentStatus parsedStatus = status != null && !status.isBlank() ? StudentStatus.valueOf(status) : null;
		String likeSearch = search != null && !search.isBlank() ? "%" + search.toLowerCase() + "%" : null;

		Page<Student> page = studentRepository.search(
				likeSearch, parsedStatus, schoolClassId, sectionId, admissionDateFrom, admissionDateTo, pageable);

		List<Long> studentIds = page.getContent().stream().map(Student::getId).toList();
		Map<Long, StudentEnrollment> currentEnrollmentByStudentId = studentEnrollmentRepository
				.findByStudentIdInAndCurrentTrue(studentIds).stream()
				.collect(Collectors.toMap(enrollment -> enrollment.getStudent().getId(), enrollment -> enrollment));
		Map<Long, StudentGuardian> primaryGuardianByStudentId = studentGuardianRepository
				.findByStudentIdInAndPrimaryGuardianTrueAndStatus(studentIds, StudentGuardianStatus.ACTIVE).stream()
				.collect(Collectors.toMap(link -> link.getStudent().getId(), link -> link));

		return PageResponse.from(page.map(student -> StudentListRowResponse.from(
				student, currentEnrollmentByStudentId.get(student.getId()), primaryGuardianByStudentId.get(student.getId()))));
	}

	@GetMapping("/{studentId}")
	public StudentResponse get(@PathVariable Long studentId) {
		Student student = studentRepository.findById(studentId)
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + studentId));
		return StudentResponse.from(student);
	}

	@PatchMapping("/{studentId}")
	@RequirePermission("STUDENT_MANAGE")
	public StudentResponse update(@PathVariable Long studentId, @RequestBody UpdateStudentRequest request) {
		Student student = studentRepository.findById(studentId)
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + studentId));
		Student updated = studentService.update(student, request.admissionSource(), request.previousSchool(),
				request.tcNumber(), request.entranceScore(), request.bloodGroup(), request.category(),
				request.nationality(), request.remarks(), request.medicalAlerts(), request.emergencyContactName(),
				request.emergencyContactPhone());
		return StudentResponse.from(updated);
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
