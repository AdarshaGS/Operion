package com.operion.performance.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.EnumParam;
import com.operion.performance.AppraisalService;
import com.operion.performance.CallerPerson;
import com.operion.performance.FeedbackSource;
import com.operion.performance.GoalStatus;
import com.operion.performance.PerformanceOverviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator side of appraisal cycles: managing cycles, rating other teachers and setting their goals. */
@RestController
@RequestMapping("/api/v1/performance")
@RequirePermission("KPI_VIEW")
public class AppraisalController {

	private final AppraisalService appraisalService;
	private final CallerPerson callerPerson;
	private final PerformanceOverviewService overviewService;

	public AppraisalController(AppraisalService appraisalService, CallerPerson callerPerson, PerformanceOverviewService overviewService) {
		this.appraisalService = appraisalService;
		this.callerPerson = callerPerson;
		this.overviewService = overviewService;
	}

	@GetMapping("/cycles")
	public List<AppraisalService.CycleView> cycles() {
		return appraisalService.cycles();
	}

	@PostMapping("/cycles")
	@RequirePermission("KPI_MANAGE")
	public AppraisalService.CycleView create(@Valid @RequestBody CreateCycleRequest request) {
		return appraisalService.createCycle(request.name(), request.startDate(), request.endDate());
	}

	@PostMapping("/cycles/{cycleId}/close")
	@RequirePermission("KPI_MANAGE")
	public AppraisalService.CycleView close(@PathVariable Long cycleId) {
		return appraisalService.closeCycle(cycleId);
	}

	@GetMapping("/cycles/{cycleId}/teachers/{personId}")
	public AppraisalResponse appraisal(@PathVariable Long cycleId, @PathVariable Long personId) {
		return AppraisalResponse.from(appraisalService.adminAppraisal(cycleId, personId, viewerPersonId()),
				overviewService.staffInfo(cycleId, personId));
	}

	@PostMapping("/cycles/{cycleId}/teachers/{personId}/feedback")
	@RequirePermission("KPI_REVIEW")
	public AppraisalService.FeedbackView feedback(@PathVariable Long cycleId, @PathVariable Long personId,
			@Valid @RequestBody FeedbackRequest request) {
		FeedbackSource source = request.source() == null ? null : EnumParam.parse(FeedbackSource.class, request.source());
		return appraisalService.giveFeedback(cycleId, personId, callerPerson.personId(), source, request.rating(), request.comment());
	}

	@PostMapping("/cycles/{cycleId}/teachers/{personId}/goals")
	@RequirePermission("KPI_REVIEW")
	public AppraisalService.GoalView addGoal(@PathVariable Long cycleId, @PathVariable Long personId, @Valid @RequestBody GoalRequest request) {
		return appraisalService.addGoal(cycleId, personId, request.title(), request.description());
	}

	@PutMapping("/goals/{goalId}")
	@RequirePermission("KPI_REVIEW")
	public AppraisalService.GoalView updateGoal(@PathVariable Long goalId, @Valid @RequestBody GoalRequest request) {
		GoalStatus status = request.status() == null ? null : EnumParam.parse(GoalStatus.class, request.status());
		return appraisalService.updateGoal(goalId, request.title(), request.description(), status);
	}

	/** The viewer's own person, if they have one - an Owner without a membership person simply has no rating of their own to flag. */
	private Long viewerPersonId() {
		try {
			return callerPerson.personId();
		} catch (RuntimeException e) {
			return null;
		}
	}
}
