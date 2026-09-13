package com.operion.platform.api;

import com.operion.platform.settings.PlatformSetting;

public record PlatformSettingsResponse(int trialDays) {

	public static PlatformSettingsResponse from(PlatformSetting setting) {
		return new PlatformSettingsResponse(setting.getTrialDays());
	}
}
