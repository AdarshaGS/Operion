package com.operion.timetable.api;

import com.operion.timetable.TeacherUnavailability;

public record UnavailabilityResponse(Long id, Long teacherPersonId, String dayOfWeek, Long periodId, String periodLabel, String reason) {

	static UnavailabilityResponse from(TeacherUnavailability u) {
		return new UnavailabilityResponse(u.getId(), u.getTeacherPerson().getId(), u.getDayOfWeek().name(),
				u.getPeriod() == null ? null : u.getPeriod().getId(), u.getPeriod() == null ? null : u.getPeriod().getLabel(), u.getReason());
	}
}
