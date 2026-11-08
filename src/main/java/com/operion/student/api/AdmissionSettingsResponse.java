package com.operion.student.api;

import com.operion.student.AdmissionSettings;

public record AdmissionSettingsResponse(String feeClearanceMode) {

	public static AdmissionSettingsResponse from(AdmissionSettings settings) {
		return new AdmissionSettingsResponse(settings.getFeeClearanceMode().name());
	}

	public static AdmissionSettingsResponse defaults() {
		return new AdmissionSettingsResponse(AdmissionSettings.DEFAULT_FEE_CLEARANCE_MODE.name());
	}
}
