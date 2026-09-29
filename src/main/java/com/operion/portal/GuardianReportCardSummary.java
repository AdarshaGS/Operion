package com.operion.portal;

import com.operion.examination.ReportCard;

public record GuardianReportCardSummary(
		Long reportCardId, String examName, Double percentage, String overallGrade, boolean passed, Integer classRank) {

	public static GuardianReportCardSummary from(ReportCard reportCard) {
		return new GuardianReportCardSummary(reportCard.getId(), reportCard.getExam().getName(), reportCard.getPercentage(),
				reportCard.getOverallGrade(), reportCard.isPassed(), reportCard.getClassRank());
	}
}
