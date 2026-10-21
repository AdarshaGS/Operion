package com.operion.performance.api;

import java.util.List;

import com.operion.performance.AppraisalService;
import com.operion.performance.PerformanceOverviewService;

/** {@code staff} is filled for the administrator's view only; a teacher looking at their own appraisal gets null. */
public record AppraisalResponse(AppraisalService.CycleView cycle, ScorecardResponse scorecard,
		List<AppraisalService.FeedbackView> feedback, List<AppraisalService.GoalView> goals, PerformanceOverviewService.StaffInfo staff) {

	public static AppraisalResponse from(AppraisalService.Appraisal a) {
		return from(a, null);
	}

	public static AppraisalResponse from(AppraisalService.Appraisal a, PerformanceOverviewService.StaffInfo staff) {
		return new AppraisalResponse(a.cycle(), ScorecardResponse.from(a.scorecard()), a.feedback(), a.goals(), staff);
	}
}
