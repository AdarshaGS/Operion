package com.operion.performance.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.performance.PerformanceOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cycle-wide read model behind the Performance dashboard: staff rows, summary figures and insights. */
@RestController
@RequestMapping("/api/v1/performance/cycles")
@RequirePermission("KPI_VIEW")
public class OverviewController {

	private final PerformanceOverviewService overviewService;

	public OverviewController(PerformanceOverviewService overviewService) {
		this.overviewService = overviewService;
	}

	@GetMapping("/summaries")
	public List<PerformanceOverviewService.CycleSummary> summaries() {
		return overviewService.cycleSummaries();
	}

	@GetMapping("/{cycleId}/overview")
	public PerformanceOverviewService.Overview overview(@PathVariable Long cycleId) {
		return overviewService.overview(cycleId);
	}
}
