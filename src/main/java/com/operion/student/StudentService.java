package com.operion.student;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.academic.Section;
import com.operion.audit.AuditLogService;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.DocumentNumberFormatter;
import com.operion.organisation.OrganisationBranding;
import com.operion.organisation.OrganisationBrandingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns student admission, year-by-year enrollment history, documents, and exit -
 * everything is a thin save except the two pieces of real business logic: enrollment's
 * insert-only promotion (mirrors AcademicService.assignTeacher's history rule) and exit
 * cascading a Student/StudentEnrollment status change, per
 * ai-context/erp-system-plan.md §2.2.
 */
@Service
public class StudentService {

	private final StudentRepository studentRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final StudentDocumentRepository studentDocumentRepository;
	private final StudentExitRepository studentExitRepository;
	private final StudentAdmissionCounterRepository studentAdmissionCounterRepository;
	private final OrganisationBrandingRepository organisationBrandingRepository;
	private final AuditLogService auditLogService;

	public StudentService(StudentRepository studentRepository, StudentEnrollmentRepository studentEnrollmentRepository,
			StudentDocumentRepository studentDocumentRepository, StudentExitRepository studentExitRepository,
			StudentAdmissionCounterRepository studentAdmissionCounterRepository,
			OrganisationBrandingRepository organisationBrandingRepository, AuditLogService auditLogService) {
		this.studentRepository = studentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.studentDocumentRepository = studentDocumentRepository;
		this.studentExitRepository = studentExitRepository;
		this.studentAdmissionCounterRepository = studentAdmissionCounterRepository;
		this.organisationBrandingRepository = organisationBrandingRepository;
		this.auditLogService = auditLogService;
	}

	/** {@code admissionNumber} is auto-generated from the org's configured format (#142) when null/blank. */
	@Transactional
	public Student admit(Person person, String admissionNumber, LocalDate admissionDate, String admissionSource,
			String previousSchool, String tcNumber, Double entranceScore, String bloodGroup, String category,
			String nationality, String remarks) {
		String resolvedAdmissionNumber =
				admissionNumber != null && !admissionNumber.isBlank() ? admissionNumber : nextAdmissionNumber(admissionDate);
		Student student = studentRepository.save(new Student(person, resolvedAdmissionNumber, admissionDate, admissionSource,
				previousSchool, tcNumber, entranceScore, bloodGroup, category, nationality, remarks));
		auditLogService.record("Student", student.getId(), "STUDENT_ADMITTED", null, student.getStatus());
		return student;
	}

	/** Atomic per-(organisation, calendar year) sequence - never SELECT MAX()+1, same pattern as FeeService. */
	private String nextAdmissionNumber(LocalDate admissionDate) {
		String template = organisationBrandingRepository.findById(TenantContext.getOrganisationId())
				.map(OrganisationBranding::getAdmissionNumberFormat)
				.orElse(OrganisationBranding.DEFAULT_ADMISSION_NUMBER_FORMAT);
		int calendarYear = admissionDate.getYear();
		StudentAdmissionCounter counter = studentAdmissionCounterRepository.findByCalendarYear(calendarYear)
				.orElseGet(() -> studentAdmissionCounterRepository.save(new StudentAdmissionCounter(calendarYear)));
		long number = counter.consumeNext();
		studentAdmissionCounterRepository.save(counter);
		return DocumentNumberFormatter.format(template, number, null, calendarYear);
	}

	/**
	 * First enrollment, or re-enrollment after an exit - requires no existing current
	 * enrollment. Use promote() to move an already-enrolled student to a new year.
	 */
	@Transactional
	public StudentEnrollment enroll(Student student, AcademicYear academicYear, Section section, Integer rollNumber,
			LocalDate enrolledDate) {
		studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()).ifPresent(existing -> {
			throw new IllegalStateException("Student " + student.getId() + " already has a current enrollment");
		});
		assertCapacityAvailable(section);
		if (student.getStatus() == StudentStatus.ADMITTED) {
			student.activate();
			studentRepository.save(student);
		}
		StudentEnrollment enrollment =
				studentEnrollmentRepository.save(new StudentEnrollment(student, academicYear, section, rollNumber, enrolledDate));
		auditLogService.record("StudentEnrollment", enrollment.getId(), "STUDENT_ENROLLED", null, enrollment.getEnrollmentStatus());
		return enrollment;
	}

	/**
	 * Closes the current enrollment (PROMOTED, or REPEATED for a same-grade repeat) and
	 * inserts a new one for the new year/section - never mutates section/academicYear on
	 * the existing row, so "which class in which year" stays queryable per student.
	 */
	@Transactional
	public StudentEnrollment promote(Student student, AcademicYear newAcademicYear, Section newSection,
			Integer rollNumber, LocalDate promotionDate, boolean repeated) {
		StudentEnrollment current = studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId())
				.orElseThrow(() -> new IllegalStateException("Student " + student.getId() + " has no current enrollment to promote from"));
		StudentEnrollmentStatus previousStatus = current.getEnrollmentStatus();
		current.close(repeated ? StudentEnrollmentStatus.REPEATED : StudentEnrollmentStatus.PROMOTED, promotionDate);
		studentEnrollmentRepository.save(current);
		assertCapacityAvailable(newSection);
		StudentEnrollment enrollment =
				studentEnrollmentRepository.save(new StudentEnrollment(student, newAcademicYear, newSection, rollNumber, promotionDate));
		auditLogService.record("StudentEnrollment", enrollment.getId(), "STUDENT_PROMOTED", previousStatus, current.getEnrollmentStatus());
		return enrollment;
	}

	/** Mid-year section move only - mutates the current row in place, per the class-level note on StudentEnrollment. */
	@Transactional
	public StudentEnrollment reassignSection(Student student, Section newSection) {
		StudentEnrollment current = studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId())
				.orElseThrow(() -> new IllegalStateException("Student " + student.getId() + " has no current enrollment"));
		if (!newSection.getId().equals(current.getSection().getId())) {
			assertCapacityAvailable(newSection);
		}
		current.reassignSection(newSection);
		return studentEnrollmentRepository.save(current);
	}

	/**
	 * Capacity is a soft application-layer limit (Section.capacity is nullable = no
	 * limit set), not a DB constraint - checked here, on every path that places a
	 * current enrollment into a section, same live-guard convention as
	 * InventoryService's negative-balance check.
	 */
	private void assertCapacityAvailable(Section section) {
		Integer capacity = section.getCapacity();
		if (capacity == null) {
			return;
		}
		int currentCount = studentEnrollmentRepository.findBySectionIdAndCurrentTrue(section.getId()).size();
		if (currentCount >= capacity) {
			throw new IllegalStateException("Section " + section.getId() + " is at capacity (" + capacity + ")");
		}
	}

	/** Records the exit event, closes the current enrollment (if any), and transitions the student's own status - one atomic action. */
	@Transactional
	public StudentExit recordExit(Student student, StudentExitType exitType, LocalDate exitDate, String reason,
			String destinationSchool, Long initiatedBy) {
		StudentExit exit = studentExitRepository.save(new StudentExit(student, exitType, exitDate, reason, destinationSchool, initiatedBy));

		studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()).ifPresent(enrollment -> {
			enrollment.close(toEnrollmentExitStatus(exitType), exitDate);
			studentEnrollmentRepository.save(enrollment);
		});

		StudentStatus previousStatus = student.getStatus();
		student.exit(toStudentExitStatus(exitType));
		studentRepository.save(student);
		auditLogService.record("Student", student.getId(), "STUDENT_EXITED", previousStatus, student.getStatus());
		return exit;
	}

	/** Re-upload: supersedes any existing ACTIVE document of the same type, then inserts the new row - never overwrites fileReference in place. */
	@Transactional
	public StudentDocument addDocument(Student student, String documentType, String fileReference, String fileName, String mimeType) {
		studentDocumentRepository.findByStudentIdAndDocumentTypeAndStatus(student.getId(), documentType, StudentDocumentStatus.ACTIVE)
				.ifPresent(existing -> {
					existing.supersede();
					studentDocumentRepository.save(existing);
				});
		return studentDocumentRepository.save(new StudentDocument(student, documentType, fileReference, fileName, mimeType));
	}

	public StudentDocument verifyDocument(StudentDocument document, DocumentVerificationStatus verificationStatus, Long verifiedBy) {
		document.verify(verificationStatus, verifiedBy, Instant.now());
		return studentDocumentRepository.save(document);
	}

	private StudentEnrollmentStatus toEnrollmentExitStatus(StudentExitType exitType) {
		return switch (exitType) {
			case TRANSFER -> StudentEnrollmentStatus.TRANSFERRED;
			case GRADUATION -> StudentEnrollmentStatus.GRADUATED;
			case WITHDRAWAL, EXPULSION -> StudentEnrollmentStatus.WITHDRAWN;
		};
	}

	private StudentStatus toStudentExitStatus(StudentExitType exitType) {
		return switch (exitType) {
			case TRANSFER -> StudentStatus.TRANSFERRED_OUT;
			case GRADUATION -> StudentStatus.GRADUATED;
			case WITHDRAWAL, EXPULSION -> StudentStatus.WITHDRAWN;
		};
	}
}
