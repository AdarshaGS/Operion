package com.operion.finance.api;

import com.operion.finance.FeeStructureGroup;

public record FeeStructureGroupResponse(Long id, String name, Long academicYearId, Long schoolClassId, String status) {

	static FeeStructureGroupResponse from(FeeStructureGroup group) {
		return new FeeStructureGroupResponse(
				group.getId(), group.getName(), group.getAcademicYear().getId(), group.getSchoolClass().getId(), group.getStatus().name());
	}
}
