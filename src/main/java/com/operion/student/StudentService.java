package com.operion.student;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.academic.Section;
import com.operion.identity.Person;
import com.operion.organisation.AcademicYear;
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

	public StudentService(StudentRepository studentRepository, StudentEnrollmentRepository studentEnrollmentRepository,
			StudentDocumentRepository studentDocumentRepository, StudentExitRepository studentExitRepository) {
		this.studentRepository = studentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.studentDocumentRepository = studentDocumentRepository;
		this.studentExitRepository = studentExitRepository;
	}

	public Student admit(Person person, String admissionNumber, LocalDate admissionDate, String admissionSource,
			String previousSchool, String tcNumber, Double entranceScore, String bloodGroup, String category,
			String nationality, String remarks) {
		return studentRepository.save(new Student(person, admissionNumber, admissionDate, admissionSource,
				previousSchool, tcNumber, entranceScore, bloodGroup, category, nationality, remarks));
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
		return studentEnrollmentRepository.save(new StudentEnrollment(student, academicYear, section, rollNumber, enrolledDate));
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
		current.close(repeated ? StudentEnrollmentStatus.REPEATED : StudentEnrollmentStatus.PROMOTED, promotionDate);
		assertCapacityAvailable(newSection);
		return studentEnrollmentRepository.save(new StudentEnrollment(student, newAcademicYear, newSection, rollNumber, promotionDate));
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

		studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId())
				.ifPresent(enrollment -> enrollment.close(toEnrollmentExitStatus(exitType), exitDate));

		student.exit(toStudentExitStatus(exitType));
		studentRepository.save(student);
		return exit;
	}

	/** Re-upload: supersedes any existing ACTIVE document of the same type, then inserts the new row - never overwrites fileReference in place. */
	@Transactional
	public StudentDocument addDocument(Student student, String documentType, String fileReference, String fileName, String mimeType) {
		studentDocumentRepository.findByStudentIdAndDocumentTypeAndStatus(student.getId(), documentType, StudentDocumentStatus.ACTIVE)
				.ifPresent(StudentDocument::supersede);
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
