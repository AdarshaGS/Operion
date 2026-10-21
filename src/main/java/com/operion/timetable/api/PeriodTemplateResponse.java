package com.operion.timetable.api;

import com.operion.timetable.PeriodTemplate;

public record PeriodTemplateResponse(Long id, String name, int periodCount, long classCount) {

	static PeriodTemplateResponse from(PeriodTemplate template, int periodCount, long classCount) {
		return new PeriodTemplateResponse(template.getId(), template.getName(), periodCount, classCount);
	}
}
