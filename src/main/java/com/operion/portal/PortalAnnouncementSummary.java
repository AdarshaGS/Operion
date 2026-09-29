package com.operion.portal;

import java.time.Instant;

import com.operion.communication.Announcement;

public record PortalAnnouncementSummary(Long id, String title, String body, Instant publishedAt) {

	public static PortalAnnouncementSummary from(Announcement announcement) {
		return new PortalAnnouncementSummary(announcement.getId(), announcement.getTitle(), announcement.getBody(),
				announcement.getPublishedAt());
	}
}
