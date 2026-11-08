package com.operion.student;

import java.time.Instant;
import java.time.LocalDate;

import java.util.Map;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.GradeLevelStatus;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
import com.operion.common.TenantContext;
import com.operion.customfield.CustomFieldService;
import com.operion.identity.Person;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearGuard;
import com.operion.organisation.DocumentNumberFormatter;
import com.operion.organisation.OrganisationBranding;
import com.operion.organisation.OrganisationBrandingRepository;
import org.springframework.dao.DataAccessException;
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
	private final SectionRepository sectionRepository;
	private final GradeLevelRepository gradeLevelRepository;
	private final StudentAdmissionCounterRepository studentAdmissionCounterRepository;
	private final OrganisationBrandingRepository organisationBrandingRepository;
	private final StudentIdGenerator studentIdGenerator;
	private final AuditLogService auditLogService;
	private final CustomFieldService customFieldService;

	/** entityType key this module registers its custom fields under - see
	 * CustomFieldDefinition's class comment. */
	private static final String CUSTOM_FIELD_ENTITY_TYPE = "STUDENT";

	public StudentService(StudentRepository studentRepository, StudentEnrollmentRepository studentEnrollmentRepository,
			StudentDocumentRepository studentDocumentRepository, StudentExitRepository studentExitRepository,
			SectionRepository sectionRepository, GradeLevelRepository gradeLevelRepository,
			StudentAdmissionCounterRepository studentAdmissionCounterRepository,
			OrganisationBrandingRepository organisationBrandingRepository, StudentIdGenerator studentIdGenerator,
			AuditLogService auditLogService, CustomFieldService customFieldService) {
		this.studentRepository = studentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.studentDocumentRepository = studentDocumentRepository;
		this.studentExitRepository = studentExitRepository;
		this.sectionRepository = sectionRepository;
		this.gradeLevelRepository = gradeLevelRepository;
		this.studentAdmissionCounterRepository = studentAdmissionCounterRepository;
		this.organisationBrandingRepository = organisationBrandingRepository;
		this.studentIdGenerator = studentIdGenerator;
		this.auditLogService = auditLogService;
		this.customFieldService = customFieldService;
	}

	/** {@code admissionNumber} is auto-generated from the org's configured format (#142) when null/blank;
	 * {@code studentId} (see StudentIdGenerator) is always system-generated - the two are deliberately
	 * separate fields/counters, see #114. */
	@Transactional
	public Student admit(Person person, String admissionNumber, LocalDate admissionDate, String admissionSource,
			String previousSchool, String tcNumber, Double entranceScore, String bloodGroup, String category,
			String nationality, String remarks, String medicalAlerts, String emergencyContactName,
			String emergencyContactPhone) {
		return admit(person, admissionNumber, admissionDate, admissionSource, previousSchool, tcNumber, entranceScore,
				bloodGroup, category, nationality, remarks, medicalAlerts, emergencyContactName, emergencyContactPhone, null);
	}

	@Transactional
	public Student admit(Person person, String admissionNumber, LocalDate admissionDate, String admissionSource,
			String previousSchool, String tcNumber, Double entranceScore, String bloodGroup, String category,
			String nationality, String remarks, String medicalAlerts, String emergencyContactName,
			String emergencyContactPhone, Map<String, Object> customFields) {
		if (admissionDate.isAfter(LocalDate.now())) {
			throw new InvalidRequestException("admissionDate cannot be in the future");
		}
		if (admissionNumber != null && !admissionNumber.isBlank() && studentRepository.findByAdmissionNumber(admissionNumber).isPresent()) {
			throw new IllegalStateException("Admission number " + admissionNumber + " already exists");
		}
		String resolvedAdmissionNumber =
				admissionNumber != null && !admissionNumber.isBlank() ? admissionNumber : nextAdmissionNumber(admissionDate);
		String studentId = studentIdGenerator.next(admissionDate);
		String customFieldsJson = customFieldService.validateAndSerialize(CUSTOM_FIELD_ENTITY_TYPE, customFields);
		Student student = studentRepository.save(new Student(person, studentId, resolvedAdmissionNumber, admissionDate,
				admissionSource, previousSchool, tcNumber, entranceScore, bloodGroup, category, nationality, remarks,
				medicalAlerts, emergencyContactName, emergencyContactPhone, customFieldsJson));
		auditLogService.record("Student", student.getId(), "STUDENT_ADMITTED", null, student.getStatus());
		return student;
	}

	public Student update(Student student, String admissionSource, String previousSchool, String tcNumber,
			Double entranceScore, String bloodGroup, String category, String nationality, String remarks,
			String medicalAlerts, String emergencyContactName, String emergencyContactPhone) {
		return update(student, admissionSource, previousSchool, tcNumber, entranceScore, bloodGroup, category,
				nationality, remarks, medicalAlerts, emergencyContactName, emergencyContactPhone, null);
	}

	/** Partial update: a null argument keeps the stored value; an optional text field sent as "" is cleared.
	 * {@code customFields} is merged over the stored values - a key left out is kept, a key sent as null/"" is cleared. */
	@Transactional
	public Student update(Student student, String admissionSource, String previousSchool, String tcNumber,
			Double entranceScore, String bloodGroup, String category, String nationality, String remarks,
			String medicalAlerts, String emergencyContactName, String emergencyContactPhone,
			Map<String, Object> customFields) {
		Map<String, Object> mergedCustomFields = new java.util.LinkedHashMap<>(customFieldService.deserialize(student.getCustomFields()));
		if (customFields != null) {
			customFields.forEach((key, value) -> {
				if (value == null || value.toString().isBlank()) {
					mergedCustomFields.remove(key);
				} else {
					mergedCustomFields.put(key, value);
				}
			});
		}
		String customFieldsJson = customFieldService.validateAndSerialize(CUSTOM_FIELD_ENTITY_TYPE, mergedCustomFields, student.getCustomFields());
		student.update(keep(student.getAdmissionSource(), admissionSource), keep(student.getPreviousSchool(), previousSchool),
				keep(student.getTcNumber(), tcNumber), entranceScore != null ? entranceScore : student.getEntranceScore(),
				keep(student.getBloodGroup(), bloodGroup), keep(student.getCategory(), category),
				keep(student.getNationality(), nationality), keep(student.getRemarks(), remarks),
				keep(student.getMedicalAlerts(), medicalAlerts), keep(student.getEmergencyContactName(), emergencyContactName),
				keep(student.getEmergencyContactPhone(), emergencyContactPhone), customFieldsJson);
		Student saved = studentRepository.save(student);
		auditLogService.record("Student", saved.getId(), "STUDENT_UPDATED", null, null);
		return saved;
	}

	private static String keep(String current, String incoming) {
		if (incoming == null) {
			return current;
		}
		return incoming.isBlank() ? null : incoming;
	}

	/** Atomic per-(organisation, calendar year) sequence - never SELECT MAX()+1, same pattern as FeeService. */
	private String nextAdmissionNumber(LocalDate admissionDate) {
		String template = organisationBrandingRepository.findById(TenantContext.getOrganisationId())
				.map(OrganisationBranding::getAdmissionNumberFormat)
				.orElse(OrganisationBranding.DEFAULT_ADMISSION_NUMBER_FORMAT);
		int calendarYear = admissionDate.getYear();
		if (!studentAdmissionCounterRepository.existsByCalendarYear(calendarYear)) {
			try {
				studentAdmissionCounterRepository.createIfMissing(calendarYear);
			} catch (DataAccessException ignored) {
				// Another admission created the row first; the locking read below picks it up.
			}
		}
		StudentAdmissionCounter counter = studentAdmissionCounterRepository.findByCalendarYear(calendarYear).orElseThrow();
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
		AcademicYearGuard.requireOpen(academicYear, "enrolling a student");
		requireSectionInYear(section, academicYear);
		studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()).ifPresent(existing -> {
			throw new IllegalStateException("Student " + student.getId() + " already has a current enrollment");
		});
		assertCapacityAvailable(section);
		if (student.getStatus() == StudentStatus.ADMITTED) {
			student.activate();
			studentRepository.save(student);
		} else if (student.getStatus() == StudentStatus.WITHDRAWN || student.getStatus() == StudentStatus.TRANSFERRED_OUT) {
			student.reactivate();
			studentRepository.save(student);
		}
		StudentEnrollment previousInYear =
				studentEnrollmentRepository.findByStudentIdAndAcademicYearId(student.getId(), academicYear.getId()).orElse(null);
		StudentEnrollment enrollment;
		if (previousInYear != null && isExitStatus(previousInYear.getEnrollmentStatus())) {
			previousInYear.reopen(section, rollNumber, enrolledDate);
			enrollment = studentEnrollmentRepository.save(previousInYear);
		} else {
			enrollment = studentEnrollmentRepository.save(new StudentEnrollment(student, academicYear, section, rollNumber, enrolledDate));
		}
		auditLogService.record("StudentEnrollment", enrollment.getId(), "STUDENT_ENROLLED", null, enrollment.getEnrollmentStatus());
		return enrollment;
	}

	/**
	 * Closes the current enrollment (PROMOTED, or REPEATED for a same-grade repeat) and
	 * inserts a new one for the new year/section - never mutates section/academicYear on
	 * the existing row, so "which class in which year" stays queryable per student.
	 * The target section is row-locked as the very first statement, so concurrent promotions into
	 * it serialise. Then every rule is checked before the current enrollment is touched, so a
	 * rejection leaves the student exactly as they were: the target year must be open, different from -
	 * and later than - the current enrollment's year, the target class must be the next grade (PROMOTE)
	 * or the same grade (REPEAT), the section must have room, and an explicit roll number must be free.
	 * A null roll number is auto-assigned as the next free one.
	 */
	@Transactional
	public StudentEnrollment promote(Student student, AcademicYear newAcademicYear, Section newSection,
			Integer rollNumber, LocalDate promotionDate, boolean repeated) {
		// The lock must come before ANY other read in this transaction. On MySQL's default REPEATABLE READ the
		// first plain (non-locking) read fixes the transaction's snapshot; if that happened before we waited for
		// the lock, the capacity count and the max roll number below would read data from before the winner of the
		// race committed - letting two promotions overshoot capacity or both take max + 1.
		Section lockedSection = lockSection(newSection);
		AcademicYearGuard.requireOpen(newAcademicYear, "promoting a student into it");
		requireSectionInYear(newSection, newAcademicYear);
		StudentEnrollment current = studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId())
				.orElseThrow(() -> new IllegalStateException("Student " + student.getId() + " has no current enrollment to promote from"));
		AcademicYearGuard.requireOpen(current.getAcademicYear(), "promoting a student out of it");
		if (current.getAcademicYear().getId().equals(newAcademicYear.getId())) {
			throw new IllegalStateException("Student " + student.getId() + " is already enrolled in academic year "
					+ newAcademicYear.getName() + "; promotion must move to a different academic year");
		}
		if (!newAcademicYear.getStartDate().isAfter(current.getAcademicYear().getStartDate())) {
			throw new IllegalStateException("Academic year " + newAcademicYear.getName() + " does not start after "
					+ current.getAcademicYear().getName() + "; a student cannot be promoted into an earlier year");
		}
		studentEnrollmentRepository.findByStudentIdAndAcademicYearId(student.getId(), newAcademicYear.getId()).ifPresent(existing -> {
			throw new IllegalStateException("Student " + student.getId() + " already has an enrollment in academic year " + newAcademicYear.getName());
		});
		requireGradeSequence(current, newSection, repeated);
		assertCapacityAvailable(lockedSection);
		if (rollNumber != null && studentEnrollmentRepository.existsBySectionIdAndRollNumber(lockedSection.getId(), rollNumber)) {
			throw new InvalidRequestException("Roll number " + rollNumber + " is already taken in this section");
		}
		Integer resolvedRollNumber = rollNumber != null ? rollNumber : nextRollNumber(lockedSection, newAcademicYear);

		StudentEnrollmentStatus previousStatus = current.getEnrollmentStatus();
		current.close(repeated ? StudentEnrollmentStatus.REPEATED : StudentEnrollmentStatus.PROMOTED, promotionDate);
		studentEnrollmentRepository.save(current);
		StudentEnrollment enrollment = studentEnrollmentRepository
				.save(new StudentEnrollment(student, newAcademicYear, newSection, resolvedRollNumber, promotionDate));
		auditLogService.record("StudentEnrollment", enrollment.getId(), "STUDENT_PROMOTED", previousStatus, current.getEnrollmentStatus());
		return enrollment;
	}

	/**
	 * PROMOTE must land in the next grade, REPEAT in the same grade. "Next" is the smallest
	 * sequence_order above the current grade's (orders may have gaps), never order + 1. A class
	 * with no grade level - not possible with the current mapping, where the column is mandatory -
	 * would be skipped rather than blocked.
	 */
	private void requireGradeSequence(StudentEnrollment current, Section targetSection, boolean repeated) {
		GradeLevel currentGrade = current.getSection().getSchoolClass().getGradeLevel();
		GradeLevel targetGrade = targetSection.getSchoolClass().getGradeLevel();
		if (currentGrade == null || targetGrade == null) {
			return;
		}
		if (repeated) {
			if (!targetGrade.getId().equals(currentGrade.getId())) {
				throw new IllegalStateException("A student repeating " + currentGrade.getName()
						+ " must be placed in " + currentGrade.getName() + ", not " + targetGrade.getName());
			}
			return;
		}
		GradeLevel nextGrade = gradeLevelRepository.findFirstByStatusAndSequenceOrderGreaterThanOrderBySequenceOrderAsc(GradeLevelStatus.ACTIVE, currentGrade.getSequenceOrder())
				.orElseThrow(() -> new IllegalStateException(
						currentGrade.getName() + " is the last grade, so there is no next grade to promote into; graduate the student instead"));
		if (targetGrade.getSequenceOrder() != nextGrade.getSequenceOrder()) {
			throw new IllegalStateException("A student in " + currentGrade.getName() + " must be promoted to "
					+ nextGrade.getName() + ", not " + targetGrade.getName());
		}
	}

	/** Re-reads the section with a row lock so the capacity/roll-number checks that follow cannot race another placement. Must run inside the caller's transaction. */
	private Section lockSection(Section section) {
		return sectionRepository.findByIdForUpdate(section.getId())
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + section.getId()));
	}

	/** Next free roll number in the section for the year; the caller holds the section lock. */
	private int nextRollNumber(Section section, AcademicYear academicYear) {
		Integer max = studentEnrollmentRepository.findMaxRollNumber(section.getId(), academicYear.getId());
		return max == null ? 1 : max + 1;
	}

	/** Mid-year section move only - mutates the current row in place, per the class-level note on StudentEnrollment. */
	@Transactional
	public StudentEnrollment reassignSection(Student student, Section newSection) {
		StudentEnrollment current = studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId())
				.orElseThrow(() -> new IllegalStateException("Student " + student.getId() + " has no current enrollment"));
		AcademicYearGuard.requireOpen(current.getAcademicYear(), "moving a student to another section");
		if (!newSection.getSchoolClass().getId().equals(current.getSection().getSchoolClass().getId())) {
			throw new InvalidRequestException("A section can only be reassigned within the student's current class");
		}
		if (!newSection.getId().equals(current.getSection().getId())) {
			assertCapacityAvailable(newSection);
		}
		Long previousSectionId = current.getSection().getId();
		current.reassignSection(newSection);
		StudentEnrollment saved = studentEnrollmentRepository.save(current);
		auditLogService.record("StudentEnrollment", saved.getId(), "STUDENT_SECTION_REASSIGNED", previousSectionId, newSection.getId());
		return saved;
	}

	private static void requireSectionInYear(Section section, AcademicYear academicYear) {
		if (!section.getSchoolClass().getAcademicYear().getId().equals(academicYear.getId())) {
			throw new InvalidRequestException("Section " + section.getId() + " does not belong to academic year " + academicYear.getName());
		}
	}

	private static boolean isExitStatus(StudentEnrollmentStatus status) {
		return status == StudentEnrollmentStatus.TRANSFERRED || status == StudentEnrollmentStatus.WITHDRAWN
				|| status == StudentEnrollmentStatus.GRADUATED;
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
		if (exitDate.isBefore(student.getAdmissionDate())) {
			throw new InvalidRequestException("exitDate cannot be before the admission date (" + student.getAdmissionDate() + ")");
		}
		studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId())
				.ifPresent(enrollment -> AcademicYearGuard.requireOpen(enrollment.getAcademicYear(), "recording a student's exit"));
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
