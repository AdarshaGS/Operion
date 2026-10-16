package com.operion.learning;

import com.operion.common.InvalidRequestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.examination.ExamSchedule;
import com.operion.examination.ExamScheduleRepository;
import com.operion.examination.ExamStatus;
import com.operion.identity.PersonRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * On-screen marking of scanned answer sheets (#230). Scoped like the rest of the module: a teacher
 * works only on the (section, subject) pairs they teach; managers/viewers see everything. Total
 * marks are kept on the sheet and are deliberately NOT posted to the marks register.
 */
@Service
@Transactional
public class AnswerSheetService {

	private static final int MAX_PAGES = 20;

	public record AnnotationInput(int pageNumber, AnnotationKind kind, double x, double y, String label, Double marks) {
	}

	public record RosterRow(StudentEnrollment enrollment, AnswerSheet sheet, int pages) {
	}

	public record SheetDetail(AnswerSheet sheet, List<AnswerSheetPage> pages, List<AnswerSheetAnnotation> annotations) {
	}

	private final AnswerSheetRepository sheetRepository;
	private final AnswerSheetPageRepository pageRepository;
	private final AnswerSheetAnnotationRepository annotationRepository;
	private final ExamScheduleRepository scheduleRepository;
	private final SectionRepository sectionRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final PersonRepository personRepository;

	public AnswerSheetService(AnswerSheetRepository sheetRepository, AnswerSheetPageRepository pageRepository,
			AnswerSheetAnnotationRepository annotationRepository, ExamScheduleRepository scheduleRepository,
			SectionRepository sectionRepository, StudentEnrollmentRepository enrollmentRepository, PersonRepository personRepository) {
		this.sheetRepository = sheetRepository;
		this.pageRepository = pageRepository;
		this.annotationRepository = annotationRepository;
		this.scheduleRepository = scheduleRepository;
		this.sectionRepository = sectionRepository;
		this.enrollmentRepository = enrollmentRepository;
		this.personRepository = personRepository;
	}

	/** Exam schedules of active exams the actor may work on (every one for managers/viewers). */
	@Transactional(readOnly = true)
	public List<ExamSchedule> schedules(LearningActor actor) {
		return scheduleRepository.findAll().stream()
				.filter(s -> s.getExam().getStatus() == ExamStatus.ACTIVE)
				.filter(s -> !sectionsOf(s, actor).isEmpty())
				.sorted(Comparator.comparing(ExamSchedule::getExamDate).reversed())
				.toList();
	}

	/** Every enrolled student of the schedule's scope (that the actor may see), with their sheet if one exists. */
	@Transactional(readOnly = true)
	public List<RosterRow> roster(LearningActor actor, Long scheduleId) {
		ExamSchedule schedule = schedule(scheduleId);
		List<Section> sections = sectionsOf(schedule, actor);
		if (sections.isEmpty()) {
			throw new AuthorizationDeniedException("You have no access to this exam schedule");
		}
		List<StudentEnrollment> enrollments = sections.stream()
				.flatMap(s -> enrollmentRepository.findBySectionIdAndCurrentTrue(s.getId()).stream())
				.sorted(Comparator.comparing((StudentEnrollment e) -> e.getSection().getId())
						.thenComparing(e -> e.getRollNumber() == null ? Integer.MAX_VALUE : e.getRollNumber()))
				.toList();
		Map<Long, AnswerSheet> sheets = sheetRepository.findByExamScheduleId(scheduleId).stream()
				.collect(Collectors.toMap(s -> s.getStudentEnrollment().getId(), s -> s));
		return enrollments.stream().map(e -> {
			AnswerSheet sheet = sheets.get(e.getId());
			int pages = sheet == null ? 0 : pageRepository.findBySheetIdOrderByPageNumber(sheet.getId()).size();
			return new RosterRow(e, sheet, pages);
		}).toList();
	}

	public AnswerSheet upload(LearningActor actor, Long scheduleId, Long enrollmentId, List<String> pageRefs) {
		ExamSchedule schedule = schedule(scheduleId);
		StudentEnrollment enrollment = enrollmentRepository.findById(enrollmentId)
				.orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
		requireInScope(schedule, enrollment);
		actor.requireWrite(enrollment.getSection().getId(), schedule.getSubject().getId());
		List<String> refs = pageRefs == null ? List.of() : pageRefs.stream().filter(r -> r != null && !r.isBlank()).map(String::trim).toList();
		if (refs.isEmpty()) {
			throw new InvalidRequestException("Attach at least one scanned page");
		}
		if (refs.size() > MAX_PAGES) {
			throw new InvalidRequestException("An answer sheet can have at most " + MAX_PAGES + " pages");
		}
		AnswerSheet sheet = sheetRepository.findByExamScheduleIdAndStudentEnrollmentId(scheduleId, enrollmentId).orElse(null);
		if (sheet == null) {
			sheet = sheetRepository.save(new AnswerSheet(schedule, enrollment, personRepository.findById(actor.personId()).orElseThrow()));
		} else if (sheet.getStatus() != AnswerSheetStatus.UPLOADED) {
			throw new IllegalStateException("This sheet is already marked; it can no longer be re-uploaded");
		} else {
			pageRepository.deleteBySheetId(sheet.getId());
			pageRepository.flush();
		}
		for (int i = 0; i < refs.size(); i++) {
			pageRepository.save(new AnswerSheetPage(sheet, i + 1, refs.get(i)));
		}
		return sheet;
	}

	@Transactional(readOnly = true)
	public SheetDetail detail(LearningActor actor, Long sheetId) {
		AnswerSheet sheet = sheet(sheetId);
		if (!actor.seesAllSections() && !actor.canWrite(sheet.getStudentEnrollment().getSection().getId(), sheet.getExamSchedule().getSubject().getId())) {
			throw new AuthorizationDeniedException("You have no access to this answer sheet");
		}
		return detailOf(sheet);
	}

	/** Replaces the sheet's annotations wholesale and records the total; total defaults to the sum of MARKS annotations. */
	public SheetDetail mark(LearningActor actor, Long sheetId, List<AnnotationInput> annotations, Double totalMarks,
			String overallRemarks, Instant now) {
		AnswerSheet sheet = sheet(sheetId);
		ExamSchedule schedule = sheet.getExamSchedule();
		actor.requireWrite(sheet.getStudentEnrollment().getSection().getId(), schedule.getSubject().getId());
		if (sheet.getStatus() == AnswerSheetStatus.RELEASED) {
			throw new IllegalStateException("This sheet has been released and can no longer be changed");
		}
		List<AnswerSheetPage> pages = pageRepository.findBySheetIdOrderByPageNumber(sheetId);
		Map<Integer, AnswerSheetPage> byNumber = new HashMap<>();
		pages.forEach(p -> byNumber.put(p.getPageNumber(), p));
		List<AnnotationInput> inputs = annotations == null ? List.of() : annotations;
		double summed = 0;
		for (AnnotationInput a : inputs) {
			if (a.kind() == null || !byNumber.containsKey(a.pageNumber())) {
				throw new InvalidRequestException("Annotation refers to an unknown page or kind");
			}
			if (a.x() < 0 || a.x() > 1 || a.y() < 0 || a.y() > 1) {
				throw new InvalidRequestException("Annotation position must be within the page");
			}
			if (a.label() != null && a.label().length() > 500) {
				throw new InvalidRequestException("Annotation text is too long");
			}
			if (a.kind() == AnnotationKind.MARKS) {
				if (a.marks() == null || a.marks() < 0) {
					throw new InvalidRequestException("A marks annotation needs marks of zero or more");
				}
				summed += a.marks();
			}
		}
		double total = totalMarks != null ? totalMarks : summed;
		if (total < 0 || total > schedule.getMaxMarks()) {
			throw new InvalidRequestException("Total marks must be between 0 and " + schedule.getMaxMarks());
		}
		annotationRepository.deleteByPageSheetId(sheetId);
		annotationRepository.flush();
		for (AnnotationInput a : inputs) {
			annotationRepository.save(new AnswerSheetAnnotation(byNumber.get(a.pageNumber()), a.kind(), a.x(), a.y(),
					blankToNull(a.label()), a.kind() == AnnotationKind.MARKS ? a.marks() : null));
		}
		sheet.mark(total, blankToNull(overallRemarks), personRepository.findById(actor.personId()).orElseThrow(), now);
		return detailOf(sheet);
	}

	public AnswerSheet release(LearningActor actor, Long sheetId, Instant now) {
		AnswerSheet sheet = sheet(sheetId);
		actor.requireWrite(sheet.getStudentEnrollment().getSection().getId(), sheet.getExamSchedule().getSubject().getId());
		sheet.release(now);
		return sheet;
	}

	/** Released sheets only - a guardian never sees a sheet before the teacher hands it back. */
	@Transactional(readOnly = true)
	public List<SheetDetail> releasedForStudent(Long studentId) {
		return sheetRepository.findByStudentEnrollmentStudentIdAndStatusOrderByReleasedAtDesc(studentId, AnswerSheetStatus.RELEASED)
				.stream().map(this::detailOf).toList();
	}

	private SheetDetail detailOf(AnswerSheet sheet) {
		return new SheetDetail(sheet, pageRepository.findBySheetIdOrderByPageNumber(sheet.getId()),
				new ArrayList<>(annotationRepository.findByPageSheetId(sheet.getId())));
	}

	/** Sections of the schedule's scope in which this actor can work the schedule's subject. */
	private List<Section> sectionsOf(ExamSchedule schedule, LearningActor actor) {
		List<Section> scope = schedule.getSection() != null ? List.of(schedule.getSection())
				: sectionRepository.findBySchoolClassId(schedule.getSchoolClass().getId());
		if (actor.seesAllSections()) {
			return scope;
		}
		return scope.stream().filter(s -> actor.canWrite(s.getId(), schedule.getSubject().getId())).toList();
	}

	private void requireInScope(ExamSchedule schedule, StudentEnrollment enrollment) {
		Section section = enrollment.getSection();
		boolean sameClass = section.getSchoolClass().getId().equals(schedule.getSchoolClass().getId());
		boolean sameSection = schedule.getSection() == null || schedule.getSection().getId().equals(section.getId());
		if (!sameClass || !sameSection || !enrollment.isCurrent()) {
			throw new InvalidRequestException("This student is not in the class/section this exam schedule covers");
		}
	}

	private ExamSchedule schedule(Long id) {
		return scheduleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Exam schedule not found: " + id));
	}

	private AnswerSheet sheet(Long id) {
		return sheetRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Answer sheet not found: " + id));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
