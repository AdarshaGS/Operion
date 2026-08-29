package com.operion.communication.api;

import java.time.Instant;
import java.util.List;

/** audienceMemberPersonIds is only used for a SELECTED_GROUP audienceType - see
 * AudienceType's class doc. scheduledAt is optional - null means the draft only publishes
 * on an explicit manual publish call, a future instant hands it to
 * ScheduledAnnouncementPublisher instead. */
public record CreateAnnouncementRequest(Long campusId, String title, String body, String audienceType, Long audienceRefId,
		List<Long> audienceMemberPersonIds, Instant scheduledAt) {
}
