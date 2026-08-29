package com.operion.communication.api;

import java.time.Instant;

import com.operion.communication.Announcement;

public record AnnouncementResponse(Long id, Long campusId, String title, String body,
		String audienceType, Long audienceRefId, String status, Instant publishedAt, Instant scheduledAt) {

	static AnnouncementResponse from(Announcement announcement) {
		return new AnnouncementResponse(announcement.getId(), announcement.getCampus() == null ? null : announcement.getCampus().getId(),
				announcement.getTitle(), announcement.getBody(), announcement.getAudienceType().name(),
				announcement.getAudienceRefId(), announcement.getStatus().name(), announcement.getPublishedAt(),
				announcement.getScheduledAt());
	}
}
