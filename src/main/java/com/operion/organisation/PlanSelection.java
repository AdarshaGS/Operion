package com.operion.organisation;

import java.time.LocalDate;

/** Optional subscription-plan input for {@link OrganisationService#provision}. */
public record PlanSelection(Long planId, LocalDate startDate) {
}
