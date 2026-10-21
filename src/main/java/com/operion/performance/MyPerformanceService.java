package com.operion.performance;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.common.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A teacher's view of their own performance. Not behind a permission - every active teacher may look at
 * themself - but off until the organisation turns on {@link KpiSettings#isTeacherSelfView()}.
 */
@Service
public class MyPerformanceService {

	private final KpiSettingsRepository settingsRepository;
	private final CallerPerson callerPerson;
	private final TeacherAssignmentRepository assignmentRepository;
	private final KpiService kpiService;
	private final AppraisalService appraisalService;

	public MyPerformanceService(KpiSettingsRepository settingsRepository, CallerPerson callerPerson,
			TeacherAssignmentRepository assignmentRepository, KpiService kpiService, AppraisalService appraisalService) {
		this.settingsRepository = settingsRepository;
		this.callerPerson = callerPerson;
		this.assignmentRepository = assignmentRepository;
		this.kpiService = kpiService;
		this.appraisalService = appraisalService;
	}

	@Transactional(readOnly = true)
	public KpiService.Scorecard scorecard(LocalDate from, LocalDate to) {
		return kpiService.scorecardFor(callingTeacher(), from, to);
	}

	@Transactional(readOnly = true)
	public List<AppraisalService.CycleView> cycles() {
		callingTeacher();
		return appraisalService.cycles();
	}

	@Transactional(readOnly = true)
	public AppraisalService.Appraisal appraisal(Long cycleId) {
		return appraisalService.selfAppraisal(cycleId, callingTeacher());
	}

	@Transactional
	public AppraisalService.FeedbackView selfAppraise(Long cycleId, int rating, String comment) {
		Long personId = callingTeacher();
		return appraisalService.giveFeedback(cycleId, personId, personId, FeedbackSource.SELF, rating, comment);
	}

	@Transactional
	public AppraisalService.GoalView addGoal(Long cycleId, String title, String description) {
		return appraisalService.addGoal(cycleId, callingTeacher(), title, description);
	}

	/** The caller's person id, once the organisation allows self-view and the caller is an active teacher. */
	private Long callingTeacher() {
		boolean enabled = settingsRepository.findByOrganisationId(TenantContext.getOrganisationId()).map(KpiSettings::isTeacherSelfView).orElse(false);
		if (!enabled) {
			throw new AuthorizationDeniedException("Teachers cannot view their own performance in this organisation");
		}
		Long personId = callerPerson.personId();
		boolean teaches = assignmentRepository.findByTeacherPersonId(personId).stream().anyMatch(a -> a.getStatus() == TeacherAssignmentStatus.ACTIVE);
		if (!teaches) {
			throw new AuthorizationDeniedException("Only teachers with an active class assignment can view this");
		}
		return personId;
	}
}
