package com.operion.timetable.api;

import com.operion.timetable.TimetableEntry;

public record TimetableEntryResponse(Long id, Long sectionId, String sectionName, String className, Long subjectId,
		String subjectName, Long teacherPersonId, String teacherName, String dayOfWeek, Long periodId) {

	static TimetableEntryResponse from(TimetableEntry entry) {
		var schoolClass = entry.getSection().getSchoolClass();
		String className = schoolClass.getDisplayName() != null ? schoolClass.getDisplayName()
				: schoolClass.getGradeLevel().getName();
		var teacher = entry.getTeacherPerson();
		return new TimetableEntryResponse(entry.getId(), entry.getSection().getId(), entry.getSection().getName(),
				className, entry.getSubject().getId(), entry.getSubject().getName(), teacher.getId(),
				teacher.getFirstName() + " " + teacher.getLastName(), entry.getDayOfWeek().name(),
				entry.getPeriod().getId());
	}
}
