package com.operion.learning.api;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.learning.Labels;
import com.operion.learning.LessonPlan;

public record LessonPlanResponse(Long id, Long sectionId, String className, String sectionName, Long subjectId,
		String subjectName, LocalDate planDate, String title, String objectives, String activities, String resources,
		String status, String authorName, Instant submittedAt, String reviewNote, String reviewedByName, Instant reviewedAt) {

	public static LessonPlanResponse from(LessonPlan p) {
		return new LessonPlanResponse(p.getId(), p.getSection().getId(), Labels.className(p.getSection()),
				p.getSection().getName(), p.getSubject().getId(), p.getSubject().getName(), p.getPlanDate(), p.getTitle(),
				p.getObjectives(), p.getActivities(), p.getResources(), p.getStatus().name(), Labels.personName(p.getAuthor()),
				p.getSubmittedAt(), p.getReviewNote(), p.getReviewedBy() == null ? null : Labels.personName(p.getReviewedBy()),
				p.getReviewedAt());
	}
}
