package com.operion.platform.crm.api;

import java.time.Instant;

import com.operion.platform.crm.Activity;

public record ActivityResponse(Long id, Long leadId, Long organisationId, String type, String body, Instant occurredAt,
		Long authorId, String authorName) {

	public static ActivityResponse from(Activity activity, String authorName) {
		return new ActivityResponse(activity.getId(), activity.getLeadId(), activity.getOrganisationId(),
				activity.getType().name(), activity.getBody(), activity.getOccurredAt(), activity.getAuthorId(), authorName);
	}
}
