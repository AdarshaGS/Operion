package com.operion.learning;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.identity.PersonRepository;
import com.operion.student.StudentEnrollment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lesson planner (#229). Teachers author for sections they teach; a caller with
 * LESSON_PLAN_REVIEW approves or rejects submitted plans. A DRAFT is private to its author.
 */
@Service
@Transactional
public class LessonPlanService {

	private final LessonPlanRepository planRepository;
	private final SectionRepository sectionRepository;
	private final SubjectRepository subjectRepository;
	private final PersonRepository personRepository;

	public LessonPlanService(LessonPlanRepository planRepository, SectionRepository sectionRepository,
			SubjectRepository subjectRepository, PersonRepository personRepository) {
		this.planRepository = planRepository;
		this.sectionRepository = sectionRepository;
		this.subjectRepository = subjectRepository;
		this.personRepository = personRepository;
	}

	public LessonPlan create(LearningActor actor, Long sectionId, Long subjectId, LocalDate planDate, String title,
			String objectives, String activities, String resources) {
		requireContent(planDate, title);
		actor.requireWrite(sectionId, subjectId);
		Section section = sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
		Subject subject = subjectRepository.findById(subjectId)
				.orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectId));
		return planRepository.save(new LessonPlan(section, subject, planDate, title.trim(), blankToNull(objectives),
				blankToNull(activities), blankToNull(resources), personRepository.findById(actor.personId()).orElseThrow()));
	}

	public LessonPlan edit(LearningActor actor, Long planId, LocalDate planDate, String title, String objectives,
			String activities, String resources) {
		requireContent(planDate, title);
		LessonPlan plan = requireOwn(actor, planId);
		plan.edit(planDate, title.trim(), blankToNull(objectives), blankToNull(activities), blankToNull(resources));
		return plan;
	}

	public LessonPlan submit(LearningActor actor, Long planId) {
		LessonPlan plan = requireOwn(actor, planId);
		plan.submit(Instant.now());
		return plan;
	}

	public LessonPlan approve(LearningActor actor, Long planId, String note) {
		actor.requireReviewer();
		LessonPlan plan = find(planId);
		plan.approve(personRepository.findById(actor.personId()).orElseThrow(), blankToNull(note), Instant.now());
		return plan;
	}

	public LessonPlan reject(LearningActor actor, Long planId, String note) {
		actor.requireReviewer();
		if (note == null || note.isBlank()) {
			throw new IllegalArgumentException("Tell the teacher what to change when rejecting a plan");
		}
		LessonPlan plan = find(planId);
		plan.reject(personRepository.findById(actor.personId()).orElseThrow(), note.trim(), Instant.now());
		return plan;
	}

	/** Reviewers and viewers see every section's plans; a teacher sees their own sections'. A DRAFT is only ever visible to its author. */
	@Transactional(readOnly = true)
	public List<LessonPlan> list(LearningActor actor, Long sectionId, LessonPlanStatus status) {
		List<LessonPlan> plans = seesAll(actor) ? planRepository.findAllByOrderByPlanDateDescIdDesc()
				: planRepository.findBySectionIdInOrderByPlanDateDescIdDesc(actor.teachingSectionIds());
		return plans.stream()
				.filter(p -> p.getStatus() != LessonPlanStatus.DRAFT || p.getAuthor().getId().equals(actor.personId()))
				.filter(p -> sectionId == null || p.getSection().getId().equals(sectionId))
				.filter(p -> status == null || p.getStatus() == status)
				.toList();
	}

	/** Approved plans in the date range - the calendar feed. */
	@Transactional(readOnly = true)
	public List<LessonPlan> calendar(LearningActor actor, LocalDate from, LocalDate to) {
		if (from == null || to == null || to.isBefore(from)) {
			throw new IllegalArgumentException("A valid date range is required");
		}
		List<LessonPlan> approved = planRepository.findByStatusAndPlanDateBetweenOrderByPlanDateAsc(LessonPlanStatus.APPROVED, from, to);
		if (seesAll(actor)) {
			return approved;
		}
		return approved.stream().filter(p -> actor.teaches(p.getSection().getId())).toList();
	}

	@Transactional(readOnly = true)
	public List<LessonPlan> calendarForChild(StudentEnrollment enrollment, LocalDate from, LocalDate to) {
		if (from == null || to == null || to.isBefore(from)) {
			throw new IllegalArgumentException("A valid date range is required");
		}
		return planRepository.findBySectionIdAndStatusAndPlanDateBetweenOrderByPlanDateAsc(enrollment.getSection().getId(),
				LessonPlanStatus.APPROVED, from, to);
	}

	private boolean seesAll(LearningActor actor) {
		return actor.seesAllSections() || actor.reviewer();
	}

	private LessonPlan find(Long planId) {
		return planRepository.findById(planId).orElseThrow(() -> new IllegalArgumentException("Lesson plan not found: " + planId));
	}

	/** Only the author (or a manager) may change a plan, and only for a section/subject they still teach. */
	private LessonPlan requireOwn(LearningActor actor, Long planId) {
		LessonPlan plan = find(planId);
		if (!actor.manager() && !plan.getAuthor().getId().equals(actor.personId())) {
			throw new AuthorizationDeniedException("Only the author can change this lesson plan");
		}
		actor.requireWrite(plan.getSection().getId(), plan.getSubject().getId());
		return plan;
	}

	private static void requireContent(LocalDate planDate, String title) {
		if (planDate == null) {
			throw new IllegalArgumentException("Date is required");
		}
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("Title is required");
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
