package com.operion.organisation.api;

import com.operion.organisation.Designation;

public record DesignationResponse(Long id, String name, String status) {

	static DesignationResponse from(Designation designation) {
		return new DesignationResponse(designation.getId(), designation.getName(), designation.getStatus().name());
	}
}
