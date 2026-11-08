package com.operion.student;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.GradeLevelStatus;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SchoolClassStatus;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SectionStatus;
import com.operion.common.InvalidRequestException;
import com.operion.examination.ReportCardRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearGuard;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only preview for the year-end promotion wizard: for every class of the source year, which class its
 * students would move into and, per student, what the school's own records suggest (promote on a passed
 * published FINAL result, graduate from the last grade, otherwise leave the call to the admin). It writes
 * nothing - no enrollment, no audit row - and decides nothing: the admin still submits explicit decisions to
 * {@link StudentPromotionService#apply}, whose guards remain the source of truth.
 * <p>
 * "Published" means a report card in {@code PUBLISHED} status (a superseded one is a replaced snapshot and is
 * ignored); a FINAL exam of the source year is the "final result". Every lookup is a bulk query - nothing is
 * read per student.
 */
@Service
public class StudentPromotionPreviewService {

	public record ClassMapping(Long sourceSchoolClassId, Long targetSchoolClassId) {
	}

	/** {@code classMap} entries override the default; an entry with a null target means "graduate this class". */
	public record Request(Long sourceAcademicYearId, Long targetAcademicYearId, LocalDate effectiveDate,
			List<ClassMapping> classMap) {
	}

	public enum Proposal { PROMOTE, REPEAT, GRADUATE, NEEDS_DECISION, ALREADY_DONE }

	/** {@code remainingCapacity} is null when the section has no capacity limit. */
	public record TargetSection(Long id, String name, Integer remainingCapacity) {
	}

	public record StudentPreview(Long studentId, String name, String admissionNumber, Long currentSectionId,
			Proposal proposedAction, String reason, Long suggestedTargetSectionId) {
	}

	/** {@code proposedAction} is PROMOTE or GRADUATE; {@code reason} explains a PROMOTE with no target class. */
	public record ClassPreview(Long sourceSchoolClassId, String label, Long proposedTargetSchoolClassId,
			StudentPromotionService.Action proposedAction, String reason, List<TargetSection> targetSections,
			List<StudentPreview> students) {
	}

	private final AcademicYearRepository academicYearRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final SectionRepository sectionRepository;
	private final GradeLevelRepository gradeLevelRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final ReportCardRepository reportCardRepository;

	public StudentPromotionPreviewService(AcademicYearRepository academicYearRepository,
			SchoolClassRepository schoolClassRepository, SectionRepository sectionRepository,
			GradeLevelRepository gradeLevelRepository, StudentEnrollmentRepository studentEnrollmentRepository,
			ReportCardRepository reportCardRepository) {
		this.academicYearRepository = academicYearRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.sectionRepository = sectionRepository;
		this.gradeLevelRepository = gradeLevelRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.reportCardRepository = reportCardRepository;
	}

	@Transactional(readOnly = true)
	public List<ClassPreview> preview(Request request) {
		if (request == null || request.sourceAcademicYearId() == null || request.targetAcademicYearId() == null) {
			throw new InvalidRequestException("A source and a target academic year are required");
		}
		if (request.effectiveDate() == null) {
			throw new InvalidRequestException("A promotion date is required");
		}
		AcademicYear sourceYear = findYear(request.sourceAcademicYearId());
		AcademicYear targetYear = findYear(request.targetAcademicYearId());
		if (sourceYear.getId().equals(targetYear.getId())) {
			throw new InvalidRequestException("The target academic year must differ from the source academic year");
		}
		AcademicYearGuard.requireOpen(targetYear, "promoting students into it");

		List<SchoolClass> sourceClasses = schoolClassRepository.findByAcademicYearId(sourceYear.getId());
		List<SchoolClass> targetClasses = schoolClassRepository.findByAcademicYearId(targetYear.getId());
		Map<Long, SchoolClass> sourceById = byId(sourceClasses);
		Map<Long, SchoolClass> targetById = byId(targetClasses);
		Map<Long, Long> overrides = validatedOverrides(request.classMap(), sourceById, targetById, sourceYear, targetYear);

		Map<Long, List<StudentEnrollment>> enrollmentsByClass = new HashMap<>();
		List<StudentEnrollment> sourceEnrollments = studentEnrollmentRepository.findCurrentOfActiveStudentsInYear(sourceYear.getId());
		for (StudentEnrollment enrollment : sourceEnrollments) {
			enrollmentsByClass.computeIfAbsent(enrollment.getSection().getSchoolClass().getId(), k -> new ArrayList<>()).add(enrollment);
		}

		Map<Long, List<Section>> targetSectionsByClass = activeSectionsByClass(targetClasses);
		Map<Long, Integer> headCounts = headCounts(targetSectionsByClass.values());
		Set<Long> alreadyInTargetYear = studentsAlreadyInYear(targetYear, sourceEnrollments);
		Map<Long, Boolean> finalResults = publishedFinalResults(sourceYear);

		Map<String, SchoolClass> targetByCampusAndGrade = new HashMap<>();
		targetClasses.stream().sorted(Comparator.comparing(SchoolClass::getId)).forEach(c -> targetByCampusAndGrade
				.putIfAbsent(c.getCampus().getId() + ":" + c.getGradeLevel().getSequenceOrder(), c));
		Map<Integer, Optional<GradeLevel>> nextGradeCache = new HashMap<>();
		// Highest grade the target year actually has a class for, per campus: a class above it has nowhere to go.
		Map<Long, Integer> topTargetOrderByCampus = new HashMap<>();
		targetClasses.forEach(c -> topTargetOrderByCampus.merge(c.getCampus().getId(), c.getGradeLevel().getSequenceOrder(), Math::max));

		List<ClassPreview> previews = new ArrayList<>();
		for (SchoolClass sourceClass : sourceClasses.stream().sorted(CLASS_ORDER).toList()) {
			List<StudentEnrollment> enrollments = enrollmentsByClass.getOrDefault(sourceClass.getId(), List.of());
			if (enrollments.isEmpty() && sourceClass.getStatus() != SchoolClassStatus.ACTIVE) {
				continue;
			}
			ClassPlan plan = planFor(sourceClass, overrides, targetById, targetByCampusAndGrade, nextGradeCache, topTargetOrderByCampus);
			List<Section> targetSections = plan.targetClass == null ? List.of()
					: targetSectionsByClass.getOrDefault(plan.targetClass.getId(), List.of());
			List<StudentPreview> students = enrollments.stream()
					.sorted(Comparator.comparing((StudentEnrollment e) -> e.getSection().getName(), String.CASE_INSENSITIVE_ORDER)
							.thenComparing(e -> fullName(e.getStudent()), String.CASE_INSENSITIVE_ORDER))
					.map(e -> studentPreview(e, plan, targetSections, alreadyInTargetYear, finalResults))
					.toList();
			previews.add(new ClassPreview(sourceClass.getId(), sourceClass.label(),
					plan.targetClass == null ? null : plan.targetClass.getId(), plan.action, plan.reason,
					targetSections.stream().map(s -> new TargetSection(s.getId(), s.getName(), remaining(s, headCounts))).toList(),
					students));
		}
		return previews;
	}

	private static final Comparator<SchoolClass> CLASS_ORDER = Comparator
			.comparingInt((SchoolClass c) -> c.getGradeLevel().getSequenceOrder())
			.thenComparing(SchoolClass::label, String.CASE_INSENSITIVE_ORDER);

	/** What a source class does: the class its students move into (null when graduating or when none exists) and why. */
	private record ClassPlan(StudentPromotionService.Action action, SchoolClass targetClass, String reason) {
	}

	private ClassPlan planFor(SchoolClass sourceClass, Map<Long, Long> overrides, Map<Long, SchoolClass> targetById,
			Map<String, SchoolClass> targetByCampusAndGrade, Map<Integer, Optional<GradeLevel>> nextGradeCache,
			Map<Long, Integer> topTargetOrderByCampus) {
		if (overrides.containsKey(sourceClass.getId())) {
			Long overrideTarget = overrides.get(sourceClass.getId());
			return overrideTarget == null
					? new ClassPlan(StudentPromotionService.Action.GRADUATE, null, "last grade")
					: new ClassPlan(StudentPromotionService.Action.PROMOTE, targetById.get(overrideTarget), null);
		}
		GradeLevel grade = sourceClass.getGradeLevel();
		// Same "smallest sequence_order above" lookup StudentService.promote() enforces, so the preview never proposes a move apply would reject.
		Optional<GradeLevel> next = nextGradeCache.computeIfAbsent(grade.getSequenceOrder(),
				order -> gradeLevelRepository.findFirstByStatusAndSequenceOrderGreaterThanOrderBySequenceOrderAsc(GradeLevelStatus.ACTIVE, order));
		if (next.isEmpty()) {
			return new ClassPlan(StudentPromotionService.Action.GRADUATE, null, "last grade");
		}
		SchoolClass target = targetByCampusAndGrade.get(sourceClass.getCampus().getId() + ":" + next.get().getSequenceOrder());
		if (target == null) {
			// The target year has classes on this campus but none at or above the next grade: this is the last class the
			// campus has, so its students graduate. If it has a higher class (a gap) or no classes at all (year not set up
			// yet) a person has to decide - never graduate a whole class because the target year was left empty.
			Integer topOrder = topTargetOrderByCampus.get(sourceClass.getCampus().getId());
			if (topOrder != null && topOrder < next.get().getSequenceOrder()) {
				return new ClassPlan(StudentPromotionService.Action.GRADUATE, null, "last class in target year");
			}
			return new ClassPlan(StudentPromotionService.Action.PROMOTE, null, "no " + next.get().getName() + " class in target year");
		}
		return new ClassPlan(StudentPromotionService.Action.PROMOTE, target, null);
	}

	private StudentPreview studentPreview(StudentEnrollment enrollment, ClassPlan plan, List<Section> targetSections,
			Set<Long> alreadyInTargetYear, Map<Long, Boolean> finalResults) {
		Student student = enrollment.getStudent();
		Long sectionId = enrollment.getSection().getId();
		String name = fullName(student);
		if (alreadyInTargetYear.contains(student.getId())) {
			return new StudentPreview(student.getId(), name, student.getAdmissionNumber(), sectionId, Proposal.ALREADY_DONE,
					"already has an enrollment in the target year", null);
		}
		if (plan.action == StudentPromotionService.Action.GRADUATE) {
			return new StudentPreview(student.getId(), name, student.getAdmissionNumber(), sectionId, Proposal.GRADUATE, plan.reason == null ? "last grade" : plan.reason, null);
		}
		if (plan.targetClass == null) {
			return new StudentPreview(student.getId(), name, student.getAdmissionNumber(), sectionId, Proposal.NEEDS_DECISION,
					plan.reason, null);
		}
		Long suggested = targetSections.stream().filter(s -> s.getName().equalsIgnoreCase(enrollment.getSection().getName()))
				.map(Section::getId).findFirst().orElse(null);
		Boolean passed = finalResults.get(enrollment.getId());
		if (passed == null) {
			return new StudentPreview(student.getId(), name, student.getAdmissionNumber(), sectionId, Proposal.NEEDS_DECISION,
					"no published final result", suggested);
		}
		if (!passed) {
			return new StudentPreview(student.getId(), name, student.getAdmissionNumber(), sectionId, Proposal.NEEDS_DECISION,
					"failed final exam", suggested);
		}
		return new StudentPreview(student.getId(), name, student.getAdmissionNumber(), sectionId, Proposal.PROMOTE,
				"passed final exam", suggested);
	}

	/** Source class id -> target class id (null = graduate). Rejects ids that are unknown or belong to the wrong year. */
	private Map<Long, Long> validatedOverrides(List<ClassMapping> classMap, Map<Long, SchoolClass> sourceById,
			Map<Long, SchoolClass> targetById, AcademicYear sourceYear, AcademicYear targetYear) {
		Map<Long, Long> overrides = new LinkedHashMap<>();
		if (classMap == null) {
			return overrides;
		}
		for (ClassMapping mapping : classMap) {
			if (mapping == null || mapping.sourceSchoolClassId() == null) {
				throw new InvalidRequestException("Each class mapping needs a sourceSchoolClassId");
			}
			if (!sourceById.containsKey(mapping.sourceSchoolClassId())) {
				throw unknownClass(mapping.sourceSchoolClassId(), sourceYear);
			}
			if (mapping.targetSchoolClassId() != null && !targetById.containsKey(mapping.targetSchoolClassId())) {
				throw unknownClass(mapping.targetSchoolClassId(), targetYear);
			}
			overrides.put(mapping.sourceSchoolClassId(), mapping.targetSchoolClassId());
		}
		return overrides;
	}

	private static InvalidRequestException unknownClass(Long classId, AcademicYear year) {
		return new InvalidRequestException("Class " + classId + " does not belong to academic year " + year.getName());
	}

	private AcademicYear findYear(Long id) {
		return academicYearRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No academic year with id " + id));
	}

	private Map<Long, List<Section>> activeSectionsByClass(List<SchoolClass> targetClasses) {
		Map<Long, List<Section>> byClass = new HashMap<>();
		if (targetClasses.isEmpty()) {
			return byClass;
		}
		for (Section section : sectionRepository.findBySchoolClassIdIn(targetClasses.stream().map(SchoolClass::getId).toList())) {
			if (section.getStatus() == SectionStatus.ACTIVE) {
				byClass.computeIfAbsent(section.getSchoolClass().getId(), k -> new ArrayList<>()).add(section);
			}
		}
		byClass.values().forEach(sections -> sections.sort(Comparator.comparing(Section::getName, String.CASE_INSENSITIVE_ORDER)));
		return byClass;
	}

	private Map<Long, Integer> headCounts(Collection<List<Section>> sectionLists) {
		List<Long> sectionIds = sectionLists.stream().flatMap(List::stream).map(Section::getId).toList();
		Map<Long, Integer> counts = new HashMap<>();
		if (sectionIds.isEmpty()) {
			return counts;
		}
		for (Object[] row : studentEnrollmentRepository.countCurrentBySectionIds(sectionIds)) {
			counts.put((Long) row[0], ((Number) row[1]).intValue());
		}
		return counts;
	}

	private static Integer remaining(Section section, Map<Long, Integer> headCounts) {
		return section.getCapacity() == null ? null : Math.max(0, section.getCapacity() - headCounts.getOrDefault(section.getId(), 0));
	}

	/** Same guard as StudentService.promote(): any enrollment of the student in the target year, whatever its status. */
	private Set<Long> studentsAlreadyInYear(AcademicYear targetYear, List<StudentEnrollment> sourceEnrollments) {
		Set<Long> studentIds = new HashSet<>();
		sourceEnrollments.forEach(e -> studentIds.add(e.getStudent().getId()));
		Set<Long> already = new HashSet<>();
		if (!studentIds.isEmpty()) {
			studentEnrollmentRepository.findByAcademicYearIdAndStudentIdIn(targetYear.getId(), studentIds)
					.forEach(e -> already.add(e.getStudent().getId()));
		}
		return already;
	}

	/** Source-year enrollment id -> passed, from its latest PUBLISHED FINAL report card. */
	private Map<Long, Boolean> publishedFinalResults(AcademicYear sourceYear) {
		Map<Long, Boolean> results = new HashMap<>();
		for (Object[] row : reportCardRepository.findPublishedFinalResultsForYear(sourceYear.getId())) {
			results.put((Long) row[0], (Boolean) row[1]);
		}
		return results;
	}

	private static Map<Long, SchoolClass> byId(List<SchoolClass> classes) {
		Map<Long, SchoolClass> map = new HashMap<>();
		classes.forEach(c -> map.put(c.getId(), c));
		return map;
	}

	private static String fullName(Student student) {
		String last = student.getPerson().getLastName();
		return last == null || last.isBlank() ? student.getPerson().getFirstName() : student.getPerson().getFirstName() + " " + last;
	}
}
