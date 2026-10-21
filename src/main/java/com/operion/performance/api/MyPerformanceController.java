package com.operion.performance.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.performance.AppraisalService;
import com.operion.performance.MyPerformanceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A teacher's own performance. Not permission-gated: the service lets in active teachers, once the organisation enables self-view. */
@RestController
@RequestMapping("/api/v1/performance/me")
public class MyPerformanceController {

	private static final int DEFAULT_PERIOD_DAYS = 30;

	private final MyPerformanceService service;

	public MyPerformanceController(MyPerformanceService service) {
		this.service = service;
	}

	@GetMapping("/scorecard")
	public ScorecardResponse scorecard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate end = to != null ? to : LocalDate.now();
		LocalDate start = from != null ? from : end.minusDays(DEFAULT_PERIOD_DAYS - 1);
		return ScorecardResponse.from(service.scorecard(start, end));
	}

	@GetMapping("/cycles")
	public List<AppraisalService.CycleView> cycles() {
		return service.cycles();
	}

	@GetMapping("/cycles/{cycleId}")
	public AppraisalResponse appraisal(@PathVariable Long cycleId) {
		return AppraisalResponse.from(service.appraisal(cycleId));
	}

	@PostMapping("/cycles/{cycleId}/self-appraisal")
	public AppraisalService.FeedbackView selfAppraise(@PathVariable Long cycleId, @Valid @RequestBody FeedbackRequest request) {
		return service.selfAppraise(cycleId, request.rating(), request.comment());
	}

	@PostMapping("/cycles/{cycleId}/goals")
	public AppraisalService.GoalView addGoal(@PathVariable Long cycleId, @Valid @RequestBody GoalRequest request) {
		return service.addGoal(cycleId, request.title(), request.description());
	}
}
