package com.operion.common.api;

import com.operion.common.Timezone;

public record TimezoneResponse(Long id, String name, String region) {

	public static TimezoneResponse from(Timezone timezone) {
		return new TimezoneResponse(timezone.getId(), timezone.getName(), timezone.getRegion());
	}
}
