package com.operion.examination.api;

import com.operion.examination.ReportCard;

public record ReportCardResponse(
		Long id, Long examId, Long studentEnrollmentId, Double totalMarksObtained, Double totalMaxMarks, Double percentage, String overallGrade) {

	static ReportCardResponse from(ReportCard reportCard) {
		return new ReportCardResponse(reportCard.getId(), reportCard.getExam().getId(), reportCard.getStudentEnrollment().getId(),
				reportCard.getTotalMarksObtained(), reportCard.getTotalMaxMarks(), reportCard.getPercentage(), reportCard.getOverallGrade());
	}
}
