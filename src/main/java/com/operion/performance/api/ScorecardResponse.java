package com.operion.performance.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.performance.KpiService;

public record ScorecardResponse(LocalDate from, LocalDate to, List<KpiDefinitionResponse> metrics, List<TeacherRow> teachers) {

	public record Cell(String metric, Double value, int sample, Double score, Double previous) {
	}

	public record TeacherRow(Long personId, String name, Double overallScore, List<Cell> cells) {
	}

	public static ScorecardResponse from(KpiService.Scorecard scorecard) {
		return new ScorecardResponse(scorecard.from(), scorecard.to(),
				scorecard.metrics().stream().map(KpiDefinitionResponse::from).toList(),
				scorecard.teachers().stream().map(row -> new TeacherRow(row.person().getId(),
						(row.person().getFirstName() + " " + row.person().getLastName()).trim(), row.overallScore(),
						row.cells().stream().map(c -> new Cell(c.metric().name(), c.value(), c.sample(), c.score(), c.previous())).toList()))
						.toList());
	}
}
