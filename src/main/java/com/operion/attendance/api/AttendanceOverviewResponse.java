package com.operion.attendance.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The Attendance page's rollup for one day - headline counts, one row per section that has enrolled students,
 * and a short activity feed. Rates use the dashboard's formula: present / marked, with PRESENT alone counting as present. */
public record AttendanceOverviewResponse(LocalDate date, int studentsEnrolled, int present, int absent, int late, int classesNotRecorded,
		int classesTotal, List<ClassRow> classes, List<Activity> activity) {

	/** status is RECORDED once a register exists for the day, otherwise PENDING; registerStatus is DRAFT/SUBMITTED/LOCKED or null.
	 * teacher is the section's active homeroom teacher, or null. ratePercent is 0 while nothing is marked. */
	public record ClassRow(Long sectionId, Long schoolClassId, String className, String sectionName, String classLabel, String teacher, int enrolled, int present, int absent, int late,
			int marked, int ratePercent, String status, String registerStatus) {
	}

	/** kind is RECORDED, PENDING or STAFF; at is null for PENDING rows (nothing happened yet). */
	public record Activity(String kind, String title, String detail, Instant at) {
	}
}
