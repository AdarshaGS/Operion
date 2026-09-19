package com.operion.billing.api;

import java.time.Instant;

import com.operion.billing.BillingService.OrganisationUsage;

public record UsageResponse(Long organisationId, int activeStudentCount, Instant lastActivityAt) {

	public static UsageResponse from(OrganisationUsage usage) {
		return new UsageResponse(usage.organisationId(), usage.activeStudentCount(), usage.lastActivityAt());
	}
}
