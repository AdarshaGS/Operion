package com.operion.timetable;

import java.util.List;

/** What the generator did for each section: slots it filled and, per subject, the periods it could not place and why. */
public record GenerationResult(int placed, int unplaced, List<SectionOutcome> sections) {

	public record SectionOutcome(Long sectionId, String sectionName, String className, int placed, List<Shortfall> shortfalls) {
	}

	public record Shortfall(Long subjectId, String subjectName, int missing, String reason) {
	}
}
