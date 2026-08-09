package com.operion.academic.api;

import com.operion.academic.Section;

public record SectionResponse(Long id, Long schoolClassId, String name, Integer capacity, String room, String status) {

	static SectionResponse from(Section section) {
		return new SectionResponse(section.getId(), section.getSchoolClass().getId(), section.getName(),
				section.getCapacity(), section.getRoom(), section.getStatus().name());
	}
}
