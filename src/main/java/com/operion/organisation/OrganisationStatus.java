package com.operion.organisation;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrganisationStatus {
	TRIAL,
	ACTIVE,
	SUSPENDED,
	ARCHIVED;

	private static final Map<OrganisationStatus, Set<OrganisationStatus>> ALLOWED_TRANSITIONS = Map.of(
			TRIAL, EnumSet.of(ACTIVE, ARCHIVED),
			ACTIVE, EnumSet.of(SUSPENDED, ARCHIVED),
			SUSPENDED, EnumSet.of(ACTIVE, ARCHIVED),
			ARCHIVED, EnumSet.noneOf(OrganisationStatus.class));

	public boolean canTransitionTo(OrganisationStatus target) {
		return ALLOWED_TRANSITIONS.get(this).contains(target);
	}
}
