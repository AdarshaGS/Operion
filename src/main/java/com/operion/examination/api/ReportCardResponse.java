package com.operion.examination.api;

import java.time.Instant;

import com.operion.examination.ReportCard;

public record ReportCardResponse(Long id, Long examId, Long studentEnrollmentId, Double totalMarksObtained, Double totalMaxMarks,
		Double percentage, String overallGrade, boolean passed, Integer classRank, String status, boolean stale, Long publishedBy, Instant publishedAt) {

	static ReportCardResponse from(ReportCard reportCard) {
		return new ReportCardResponse(reportCard.getId(), reportCard.getExam().getId(), reportCard.getStudentEnrollment().getId(),
				reportCard.getTotalMarksObtained(), reportCard.getTotalMaxMarks(), reportCard.getPercentage(), reportCard.getOverallGrade(),
				reportCard.isPassed(), reportCard.getClassRank(), reportCard.getStatus().name(), reportCard.isStale(),
				reportCard.getCreatedBy(), reportCard.getCreatedAt());
	}
}
