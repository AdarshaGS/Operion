package com.operion.academic.api;

import com.operion.academic.AcademicConfiguration;

public record AcademicConfigurationResponse(String schoolStartTime, String schoolEndTime) {

	static AcademicConfigurationResponse from(AcademicConfiguration configuration) {
		return new AcademicConfigurationResponse(
				configuration.getSchoolStartTime() != null ? configuration.getSchoolStartTime().toString() : null,
				configuration.getSchoolEndTime() != null ? configuration.getSchoolEndTime().toString() : null);
	}
}
