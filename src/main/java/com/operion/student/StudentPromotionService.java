package com.operion.student;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.common.InvalidRequestException;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearGuard;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.stereotype.Service;

/**
 * Year-end promotion for a whole class at once: one explicit decision per student
 * (PROMOTE / REPEAT into a section of the target year, or GRADUATE), never an inferred
 * "next grade" - schools differ on streams, section reshuffles and who repeats.
 * Deliberately not {@code @Transactional}: each decision runs through StudentService's own
 * transactional promote()/recordExit(), so one failing student (section full, no current
 * enrollment) is reported and the rest still go through. A CLOSED academic year is different - it
 * is a property of the whole request, not of one student - so apply() checks the target year and
 * every student's current (source) year up front and rejects the run with a 409 before any student
 * is touched (StudentService.promote/recordExit still guard each one on their own).
 */
@Service
public class StudentPromotionService {

	public enum Action { PROMOTE, REPEAT, GRADUATE }

	public record Decision(Long studentId, Action action, Long targetSectionId, Integer rollNumber) {
	}

	/** {@code skipped} marks a decision that was already applied (see alreadyApplied; a graduate is already GRADUATED) -
	 * it is still {@code succeeded} with no error, so re-running a batch is safe. */
	public record Outcome(Long studentId, Action action, boolean succeeded, boolean skipped, String error) {
	}

	private final StudentService studentService;
	private final StudentRepository studentRepository;
	private final AcademicYearRepository academicYearRepository;
	private final SectionRepository sectionRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;

	public StudentPromotionService(StudentService studentService, StudentRepository studentRepository,
			AcademicYearRepository academicYearRepository, SectionRepository sectionRepository,
			StudentEnrollmentRepository studentEnrollmentRepository) {
		this.studentService = studentService;
		this.studentRepository = studentRepository;
		this.academicYearRepository = academicYearRepository;
		this.sectionRepository = sectionRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
	}

	public List<Outcome> apply(Long targetAcademicYearId, LocalDate effectiveDate, List<Decision> decisions, Long initiatedBy) {
		if (effectiveDate == null) {
			throw new InvalidRequestException("A promotion date is required");
		}
		if (decisions == null || decisions.isEmpty()) {
			throw new InvalidRequestException("At least one student decision is required");
		}
		boolean needsTargetYear = decisions.stream().anyMatch(d -> d.action() != Action.GRADUATE);
		AcademicYear targetYear = null;
		if (needsTargetYear) {
			if (targetAcademicYearId == null) {
				throw new InvalidRequestException("A target academic year is required to promote or repeat students");
			}
			targetYear = academicYearRepository.findById(targetAcademicYearId)
					.orElseThrow(() -> new IllegalArgumentException("No academic year with id " + targetAcademicYearId));
		}

		requireYearsOpen(targetYear, decisions);

		List<Outcome> outcomes = new ArrayList<>();
		for (Decision decision : decisions) {
			try {
				boolean skipped = applyOne(decision, targetYear, effectiveDate, initiatedBy);
				outcomes.add(new Outcome(decision.studentId(), decision.action(), true, skipped, null));
			} catch (RuntimeException ex) {
				outcomes.add(new Outcome(decision.studentId(), decision.action(), false, false, ex.getMessage()));
			}
		}
		return outcomes;
	}

	/** All-or-nothing: a closed target year, or a student whose current enrolment sits in a closed year, rejects the whole run. */
	private void requireYearsOpen(AcademicYear targetYear, List<Decision> decisions) {
		AcademicYearGuard.requireOpen(targetYear, "promoting students into it");
		List<Long> studentIds = decisions.stream().map(Decision::studentId).filter(Objects::nonNull).distinct().toList();
		for (StudentEnrollment current : studentEnrollmentRepository.findByStudentIdInAndCurrentTrue(studentIds)) {
			AcademicYearGuard.requireOpen(current.getAcademicYear(), "promoting or exiting students in it");
		}
	}

	/**
	 * A decision counts as already applied only when it is what got the student where they are: they are current in the
	 * requested target-year section AND their most recent earlier enrollment was closed with the status this decision
	 * produces (PROMOTED for PROMOTE, REPEATED for REPEAT). Merely sitting in the requested section - say after a manual
	 * enrol, or a promote where a repeat was asked - is not "applied"; it falls through to promote(), whose guards reject it.
	 */
	private boolean alreadyApplied(Student student, Decision decision, AcademicYear targetYear) {
		List<StudentEnrollment> enrollments = studentEnrollmentRepository.findByStudentId(student.getId());
		boolean currentInRequestedSection = enrollments.stream().anyMatch(e -> e.isCurrent()
				&& e.getAcademicYear().getId().equals(targetYear.getId()) && e.getSection().getId().equals(decision.targetSectionId()));
		if (!currentInRequestedSection) {
			return false;
		}
		StudentEnrollmentStatus expectedPreviousStatus =
				decision.action() == Action.REPEAT ? StudentEnrollmentStatus.REPEATED : StudentEnrollmentStatus.PROMOTED;
		return enrollments.stream()
				.filter(e -> !e.getAcademicYear().getId().equals(targetYear.getId()))
				.max(Comparator.comparing((StudentEnrollment e) -> e.getAcademicYear().getStartDate()))
				.filter(e -> !e.isCurrent() && e.getEnrollmentStatus() == expectedPreviousStatus)
				.isPresent();
	}

	/** @return true when the decision had already been applied and nothing was done. */
	private boolean applyOne(Decision decision, AcademicYear targetYear, LocalDate effectiveDate, Long initiatedBy) {
		if (decision.studentId() == null || decision.action() == null) {
			throw new InvalidRequestException("Each decision needs a studentId and an action");
		}
		Student student = studentRepository.findById(decision.studentId())
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + decision.studentId()));
		if (decision.action() == Action.GRADUATE) {
			if (student.getStatus() == StudentStatus.GRADUATED) {
				return true;
			}
			studentService.recordExit(student, StudentExitType.GRADUATION, effectiveDate, "Graduated", null, initiatedBy);
			return false;
		}
		if (decision.targetSectionId() == null) {
			throw new InvalidRequestException("A target section is required to " + decision.action().name().toLowerCase());
		}
		if (alreadyApplied(student, decision, targetYear)) {
			return true;
		}
		Section section = sectionRepository.findById(decision.targetSectionId())
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + decision.targetSectionId()));
		studentService.promote(student, targetYear, section, decision.rollNumber(), effectiveDate, decision.action() == Action.REPEAT);
		return false;
	}
}
