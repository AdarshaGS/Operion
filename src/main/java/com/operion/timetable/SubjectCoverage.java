package com.operion.timetable;

/** How many of a subject's weekly periods a section's timetable has placed. requiredPerWeek is null when the class sets no quota. */
public record SubjectCoverage(Long subjectId, String subjectName, Integer requiredPerWeek, long placed) {

	public long remaining() {
		return requiredPerWeek == null ? 0 : Math.max(0, requiredPerWeek - placed);
	}
}
