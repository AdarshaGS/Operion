package com.operion.communication.api;

import java.time.Instant;
import java.util.List;

/** audienceMemberPersonIds is only used for a SELECTED_GROUP audienceType - see
 * AudienceType's class doc. scheduledAt is optional - null means the draft only publishes
 * on an explicit manual publish call, a future instant hands it to
 * ScheduledAnnouncementPublisher instead. sendOnNonWorkingDays (null = false) lets a
 * scheduled announcement go out on a holiday instead of waiting for the next working day. */
public record CreateAnnouncementRequest(Long campusId, String title, String body, String audienceType, Long audienceRefId,
		List<Long> audienceMemberPersonIds, Instant scheduledAt,
		Boolean sendOnNonWorkingDays) {
}
