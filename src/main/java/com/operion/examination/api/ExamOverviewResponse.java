package com.operion.examination.api;

import java.time.LocalDate;
import java.util.List;

/** The Examinations page's rollup for one academic year - headline counts, one row per exam, and the three side cards.
 * Everything derives from schedules, marks registers and published report cards, so an exam with no schedule is simply DRAFT. */
public record ExamOverviewResponse(int totalExams, int upcomingExams, NextExam nextExam, int marksPendingSubjects, int marksPendingClasses,
		int resultsPublishedExams, PublishedExam lastPublished, List<ExamRow> exams, List<UpcomingSchedule> upcoming,
		List<ClassProgress> marksByClass, ResultSummary resultSummary) {

	public record NextExam(Long examId, String name, LocalDate startDate) {
	}

	public record PublishedExam(Long examId, String name, LocalDate endDate) {
	}

	/** resultStatus is PUBLISHED, IN_PROGRESS, NOT_STARTED or DRAFT (no schedule yet). marksEntryPercent is the share of
	 * schedules whose marks register is SUBMITTED or APPROVED. startDate/endDate/classes are null/empty with no schedule. */
	public record ExamRow(Long id, String name, String examType, String status, String classes, LocalDate startDate, LocalDate endDate,
			int scheduleCount, int marksEntryPercent, String resultStatus) {
	}

	public record UpcomingSchedule(Long examId, Long scheduleId, String examName, LocalDate examDate, String classLabel, String subjectName) {
	}

	public record ClassProgress(Long schoolClassId, String classLabel, int percent) {
	}

	/** passRate and the grade counts cover every PUBLISHED report card in force; both are null/empty when none is published. */
	public record ResultSummary(Integer passRate, int reportCards, List<GradeCount> grades) {
	}

	public record GradeCount(String grade, int count) {
	}
}
