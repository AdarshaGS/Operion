package com.operion.timetable.api;

import java.time.LocalDate;
import java.time.LocalTime;

import com.operion.timetable.TimetableEntry;
import com.operion.timetable.TimetableSubstitution;

public record SubstitutionResponse(Long id, LocalDate date, Long entryId, Long sectionId, String sectionName, String className,
		String subjectName, Long periodId, String periodLabel, LocalTime startTime, LocalTime endTime, Long absentTeacherPersonId,
		String absentTeacherName, Long substituteTeacherPersonId, String substituteTeacherName, String reason) {

	static SubstitutionResponse from(TimetableSubstitution s) {
		TimetableEntry entry = s.getEntry();
		var schoolClass = entry.getSection().getSchoolClass();
		String className = schoolClass.getDisplayName() != null ? schoolClass.getDisplayName() : schoolClass.getGradeLevel().getName();
		return new SubstitutionResponse(s.getId(), s.getDate(), entry.getId(), entry.getSection().getId(), entry.getSection().getName(),
				className, entry.getSubject().getName(), entry.getPeriod().getId(), entry.getPeriod().getLabel(),
				entry.getPeriod().getStartTime(), entry.getPeriod().getEndTime(), s.getAbsentTeacher().getId(),
				s.getAbsentTeacher().getFirstName() + " " + s.getAbsentTeacher().getLastName(), s.getSubstituteTeacher().getId(),
				s.getSubstituteTeacher().getFirstName() + " " + s.getSubstituteTeacher().getLastName(), s.getReason());
	}
}
