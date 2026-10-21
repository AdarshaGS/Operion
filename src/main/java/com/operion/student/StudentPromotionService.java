package com.operion.student;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.common.InvalidRequestException;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.stereotype.Service;

/**
 * Year-end promotion for a whole class at once: one explicit decision per student
 * (PROMOTE / REPEAT into a section of the target year, or GRADUATE), never an inferred
 * "next grade" - schools differ on streams, section reshuffles and who repeats.
 * Deliberately not {@code @Transactional}: each decision runs through StudentService's own
 * transactional promote()/recordExit(), so one failing student (section full, no current
 * enrollment) is reported and the rest still go through.
 */
@Service
public class StudentPromotionService {

	public enum Action { PROMOTE, REPEAT, GRADUATE }

	public record Decision(Long studentId, Action action, Long targetSectionId, Integer rollNumber) {
	}

	public record Outcome(Long studentId, Action action, boolean succeeded, String error) {
	}

	private final StudentService studentService;
	private final StudentRepository studentRepository;
	private final AcademicYearRepository academicYearRepository;
	private final SectionRepository sectionRepository;

	public StudentPromotionService(StudentService studentService, StudentRepository studentRepository,
			AcademicYearRepository academicYearRepository, SectionRepository sectionRepository) {
		this.studentService = studentService;
		this.studentRepository = studentRepository;
		this.academicYearRepository = academicYearRepository;
		this.sectionRepository = sectionRepository;
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

		List<Outcome> outcomes = new ArrayList<>();
		for (Decision decision : decisions) {
			try {
				applyOne(decision, targetYear, effectiveDate, initiatedBy);
				outcomes.add(new Outcome(decision.studentId(), decision.action(), true, null));
			} catch (RuntimeException ex) {
				outcomes.add(new Outcome(decision.studentId(), decision.action(), false, ex.getMessage()));
			}
		}
		return outcomes;
	}

	private void applyOne(Decision decision, AcademicYear targetYear, LocalDate effectiveDate, Long initiatedBy) {
		if (decision.studentId() == null || decision.action() == null) {
			throw new InvalidRequestException("Each decision needs a studentId and an action");
		}
		Student student = studentRepository.findById(decision.studentId())
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + decision.studentId()));
		if (decision.action() == Action.GRADUATE) {
			studentService.recordExit(student, StudentExitType.GRADUATION, effectiveDate, "Graduated", null, initiatedBy);
			return;
		}
		if (decision.targetSectionId() == null) {
			throw new InvalidRequestException("A target section is required to " + decision.action().name().toLowerCase());
		}
		Section section = sectionRepository.findById(decision.targetSectionId())
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + decision.targetSectionId()));
		studentService.promote(student, targetYear, section, decision.rollNumber(), effectiveDate, decision.action() == Action.REPEAT);
	}
}
