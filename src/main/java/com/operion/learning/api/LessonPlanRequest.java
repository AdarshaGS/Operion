package com.operion.learning.api;

import java.time.LocalDate;

/** sectionId/subjectId are only read on create; an edit keeps the plan's section and subject. */
public record LessonPlanRequest(Long sectionId, Long subjectId, LocalDate planDate, String title, String objectives,
		String activities, String resources) {
}
