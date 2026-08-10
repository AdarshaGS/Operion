package com.operion.communication.api;

import com.operion.communication.NotificationPreference;

public record NotificationPreferenceResponse(Long id, String channel, boolean enabled) {

	static NotificationPreferenceResponse from(NotificationPreference preference) {
		return new NotificationPreferenceResponse(preference.getId(), preference.getChannel().name(), preference.isEnabled());
	}
}
