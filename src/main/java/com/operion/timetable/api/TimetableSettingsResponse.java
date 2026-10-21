package com.operion.timetable.api;

import com.operion.timetable.TimetableSettings;

public record TimetableSettingsResponse(int gapMinutes, Integer maxTeacherPeriodsPerDay, Integer maxTeacherPeriodsPerWeek) {

	static TimetableSettingsResponse from(TimetableSettings settings) {
		return new TimetableSettingsResponse(settings.getGapMinutes(), settings.getMaxTeacherPeriodsPerDay(),
				settings.getMaxTeacherPeriodsPerWeek());
	}
}
