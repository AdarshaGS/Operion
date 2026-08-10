package com.operion.communication;

/** EMAIL/SMS are reserved for when an actual provider integration is built - v1 only fans out IN_APP. */
public enum NotificationChannel {
	IN_APP,
	EMAIL,
	SMS
}
