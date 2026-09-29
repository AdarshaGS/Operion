package com.operion.academic.api;

import java.time.LocalDate;

import com.operion.academic.SchoolEvent;

public record SchoolEventResponse(Long id, Long campusId, LocalDate date, String title, String description) {

	static SchoolEventResponse from(SchoolEvent event) {
		return new SchoolEventResponse(event.getId(), event.getCampus() != null ? event.getCampus().getId() : null, event.getDate(),
				event.getTitle(), event.getDescription());
	}
}
