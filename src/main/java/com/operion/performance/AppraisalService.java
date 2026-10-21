package com.operion.performance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appraisal cycles: reviewer ratings, self-appraisal and goals around the scorecard. Administrators see
 * everything with names. A teacher sees their own self-appraisal and goals while a cycle is open, and the
 * manager and peer ratings only once it is closed - peer ratings without the reviewer's name.
 */
@Service
public class AppraisalService {

	public record CycleView(Long id, String name, LocalDate startDate, LocalDate endDate, AppraisalCycleStatus status) {

		static CycleView of(AppraisalCycle c) {
			return new CycleView(c.getId(), c.getName(), c.getStartDate(), c.getEndDate(), c.getStatus());
		}
	}

	/** {@code mine} marks the rating the viewer gave themself, so the form can open pre-filled with it. */
	public record FeedbackView(Long id, FeedbackSource source, int rating, String comment, String reviewerName, boolean mine,
			Instant updatedAt) {
	}

	public record GoalView(Long id, String title, String description, GoalStatus status, Instant updatedAt) {

		static GoalView of(TeacherGoal g) {
			return new GoalView(g.getId(), g.getTitle(), g.getDescription(), g.getStatus(), g.getUpdatedAt());
		}
	}

	/** What the audit trail keeps of a rating, so a revised rating leaves the earlier one on record. */
	private record FeedbackSnapshot(FeedbackSource source, int rating, String comment) {

		static FeedbackSnapshot of(TeacherFeedback f) {
			return new FeedbackSnapshot(f.getSource(), f.getRating(), f.getComment());
		}
	}

	private record GoalSnapshot(String title, String description, GoalStatus status) {

		static GoalSnapshot of(TeacherGoal g) {
			return new GoalSnapshot(g.getTitle(), g.getDescription(), g.getStatus());
		}
	}

	public record Appraisal(CycleView cycle, KpiService.Scorecard scorecard, List<FeedbackView> feedback, List<GoalView> goals) {
	}

	private final AppraisalCycleRepository cycleRepository;
	private final TeacherFeedbackRepository feedbackRepository;
	private final TeacherGoalRepository goalRepository;
	private final TeacherAssignmentRepository assignmentRepository;
	private final PersonRepository personRepository;
	private final KpiService kpiService;
	private final AuditLogService auditLogService;

	public AppraisalService(AppraisalCycleRepository cycleRepository, TeacherFeedbackRepository feedbackRepository,
			TeacherGoalRepository goalRepository, TeacherAssignmentRepository assignmentRepository, PersonRepository personRepository,
			KpiService kpiService, AuditLogService auditLogService) {
		this.cycleRepository = cycleRepository;
		this.feedbackRepository = feedbackRepository;
		this.goalRepository = goalRepository;
		this.assignmentRepository = assignmentRepository;
		this.personRepository = personRepository;
		this.kpiService = kpiService;
		this.auditLogService = auditLogService;
	}

	@Transactional(readOnly = true)
	public List<CycleView> cycles() {
		return cycleRepository.findAllByOrderByStartDateDescIdDesc().stream().map(CycleView::of).toList();
	}

	@Transactional
	public CycleView createCycle(String name, LocalDate startDate, LocalDate endDate) {
		if (name == null || name.isBlank()) {
			throw new InvalidRequestException("A cycle needs a name");
		}
		if (cycleRepository.existsByNameIgnoreCase(name.trim())) {
			throw new InvalidRequestException("A cycle named " + name.trim() + " already exists");
		}
		AppraisalCycle saved = cycleRepository.save(new AppraisalCycle(name.trim(), startDate, endDate));
		auditLogService.record("AppraisalCycle", saved.getId(), "CREATE", null, CycleView.of(saved));
		return CycleView.of(saved);
	}

	@Transactional
	public CycleView closeCycle(Long cycleId) {
		AppraisalCycle cycle = cycle(cycleId);
		CycleView before = CycleView.of(cycle);
		cycle.close();
		CycleView after = CycleView.of(cycleRepository.save(cycle));
		auditLogService.record("AppraisalCycle", cycleId, "CLOSE", before, after);
		return after;
	}

	@Transactional(readOnly = true)
	public Appraisal adminAppraisal(Long cycleId, Long teacherPersonId) {
		return adminAppraisal(cycleId, teacherPersonId, null);
	}

	/** {@code viewerPersonId} is the administrator looking, so their own rating comes back flagged {@code mine}. */
	@Transactional(readOnly = true)
	public Appraisal adminAppraisal(Long cycleId, Long teacherPersonId, Long viewerPersonId) {
		AppraisalCycle cycle = cycle(cycleId);
		List<FeedbackView> feedback = feedbackRepository.findByCycleIdAndTeacherId(cycleId, teacherPersonId).stream()
				.map(f -> view(f, true, viewerPersonId)).toList();
		return appraisal(cycle, teacherPersonId, feedback);
	}

	@Transactional(readOnly = true)
	public Appraisal selfAppraisal(Long cycleId, Long teacherPersonId) {
		AppraisalCycle cycle = cycle(cycleId);
		List<FeedbackView> feedback = feedbackRepository.findByCycleIdAndTeacherId(cycleId, teacherPersonId).stream()
				.filter(f -> !cycle.isOpen() || f.getSource() == FeedbackSource.SELF)
				.map(f -> view(f, f.getSource() != FeedbackSource.PEER, teacherPersonId)).toList();
		return appraisal(cycle, teacherPersonId, feedback);
	}

	/** A teacher rating themself passes the same person as teacher and reviewer; anyone else needs MANAGER or PEER as the source. */
	@Transactional
	public FeedbackView giveFeedback(Long cycleId, Long teacherPersonId, Long reviewerPersonId, FeedbackSource requested, int rating,
			String comment) {
		AppraisalCycle cycle = cycle(cycleId);
		cycle.requireOpen();
		Person teacher = activeTeacher(teacherPersonId);
		Person reviewer = personRepository.findById(reviewerPersonId).orElseThrow(() -> new IllegalArgumentException("No reviewer"));
		boolean self = teacherPersonId.equals(reviewerPersonId);
		FeedbackSource source;
		if (self) {
			source = FeedbackSource.SELF;
		} else if (requested == FeedbackSource.MANAGER || requested == FeedbackSource.PEER) {
			source = requested;
		} else {
			throw new InvalidRequestException("Rate another teacher as MANAGER or PEER");
		}
		TeacherFeedback existing = feedbackRepository.findByCycleIdAndTeacherIdAndReviewerId(cycleId, teacherPersonId, reviewerPersonId)
				.orElse(null);
		FeedbackSnapshot before = existing == null ? null : FeedbackSnapshot.of(existing);
		TeacherFeedback feedback;
		if (existing == null) {
			feedback = new TeacherFeedback(cycle, teacher, reviewer, source, rating, comment);
		} else {
			existing.revise(source, rating, comment);
			feedback = existing;
		}
		TeacherFeedback saved = feedbackRepository.save(feedback);
		auditLogService.record("TeacherFeedback", saved.getId(), before == null ? "CREATE" : "REVISE", before, FeedbackSnapshot.of(saved));
		return view(saved, true, reviewerPersonId);
	}

	@Transactional
	public GoalView addGoal(Long cycleId, Long teacherPersonId, String title, String description) {
		AppraisalCycle cycle = cycle(cycleId);
		cycle.requireOpen();
		TeacherGoal saved = goalRepository.save(new TeacherGoal(cycle, activeTeacher(teacherPersonId), title, description));
		auditLogService.record("TeacherGoal", saved.getId(), "CREATE", null, GoalSnapshot.of(saved));
		return GoalView.of(saved);
	}

	@Transactional
	public GoalView updateGoal(Long goalId, String title, String description, GoalStatus status) {
		TeacherGoal goal = goalRepository.findById(goalId).orElseThrow(() -> new IllegalArgumentException("No goal with id " + goalId));
		GoalSnapshot before = GoalSnapshot.of(goal);
		goal.edit(title, description);
		if (status != null) {
			goal.markAs(status);
		}
		TeacherGoal saved = goalRepository.save(goal);
		auditLogService.record("TeacherGoal", saved.getId(), "UPDATE", before, GoalSnapshot.of(saved));
		return GoalView.of(saved);
	}

	private Appraisal appraisal(AppraisalCycle cycle, Long teacherPersonId, List<FeedbackView> feedback) {
		return new Appraisal(CycleView.of(cycle), kpiService.scorecardFor(teacherPersonId, cycle.getStartDate(), cycle.getEndDate()),
				feedback, goalRepository.findByCycleIdAndTeacherIdOrderByIdAsc(cycle.getId(), teacherPersonId).stream().map(GoalView::of).toList());
	}

	private static FeedbackView view(TeacherFeedback f, boolean named, Long viewerPersonId) {
		String name = named ? (f.getReviewer().getFirstName() + " " + f.getReviewer().getLastName()).trim() : null;
		boolean mine = viewerPersonId != null && viewerPersonId.equals(f.getReviewer().getId());
		return new FeedbackView(f.getId(), f.getSource(), f.getRating(), f.getComment(), name, mine, f.getUpdatedAt());
	}

	private AppraisalCycle cycle(Long id) {
		return cycleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No appraisal cycle with id " + id));
	}

	private Person activeTeacher(Long personId) {
		boolean teaches = assignmentRepository.findByTeacherPersonId(personId).stream().anyMatch(a -> a.getStatus() == TeacherAssignmentStatus.ACTIVE);
		if (!teaches) {
			throw new InvalidRequestException("That person has no active class assignment");
		}
		return personRepository.findById(personId).orElseThrow(() -> new IllegalArgumentException("No person with id " + personId));
	}
}
