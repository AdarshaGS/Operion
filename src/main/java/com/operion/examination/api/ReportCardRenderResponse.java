package com.operion.examination.api;

import java.time.Instant;
import java.util.List;

/**
 * Resolved data for one report card - branding + template style + student/exam data with
 * an already-computed subject marks table - not a rendered file (no PDF/image dependency
 * exists in this project; same rendering-scope call as IdCardRenderResponse / Letter
 * Formats #31). The frontend lays this out and prints it via the browser. Per #243.
 */
public record ReportCardRenderResponse(
		String logoRef, String stampRef, String signatureRef, String schoolNameOverride, String addressLine, String affiliationText, String footerText,
		String templateStyle, String pageSize, String fontStyle, int fontSize, String headerSubtext,
		String studentName, String admissionNumber, String className, String sectionName, String examName, String examType, String academicYearName,
		List<SubjectMark> subjects,
		Double totalMarksObtained, Double totalMaxMarks, Double percentage, String overallGrade, boolean passed, Integer classRank,
		String status, boolean stale, Long publishedBy, Instant publishedAt) {

	public record SubjectMark(String subjectName, Double maxMarks, Double passMarks, Double marksObtained, boolean absent, boolean passed, Integer rank) {
	}
}
