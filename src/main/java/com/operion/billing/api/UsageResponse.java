package com.operion.billing.api;

import com.operion.billing.BillingService.OrganisationUsage;

public record UsageResponse(Long organisationId, int activeStudentCount) {

	public static UsageResponse from(OrganisationUsage usage) {
		return new UsageResponse(usage.organisationId(), usage.activeStudentCount());
	}
}
