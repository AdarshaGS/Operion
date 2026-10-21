package com.operion.timetable.api;

import java.time.Instant;
import java.util.List;

import com.operion.timetable.SubjectCoverage;

/** unplacedPeriods is the sum of every quota's shortfall - zero means the section's timetable is complete against its quotas. */
public record SectionTimetableSummaryResponse(Long sectionId, int placedSlots, long unplacedPeriods, List<SubjectLine> subjects,
		boolean published, Instant publishedAt) {

	public record SubjectLine(Long subjectId, String subjectName, Integer requiredPerWeek, long placed, long remaining) {
	}

	public static SectionTimetableSummaryResponse of(Long sectionId, List<SubjectCoverage> coverage, Instant publishedAt) {
		List<SubjectLine> lines = coverage.stream()
				.map(c -> new SubjectLine(c.subjectId(), c.subjectName(), c.requiredPerWeek(), c.placed(), c.remaining())).toList();
		return new SectionTimetableSummaryResponse(sectionId, coverage.stream().mapToInt(c -> (int) c.placed()).sum(),
				coverage.stream().mapToLong(SubjectCoverage::remaining).sum(), lines, publishedAt != null, publishedAt);
	}
}
