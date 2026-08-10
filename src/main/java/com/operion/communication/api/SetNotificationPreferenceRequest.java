package com.operion.communication.api;

public record SetNotificationPreferenceRequest(String channel, boolean enabled) {
}
