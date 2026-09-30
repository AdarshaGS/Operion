package com.operion.platform.crm;

/** Which record a contact/activity/task hangs off: exactly one of a lead or an organisation. */
public record CrmOwner(Long leadId, Long organisationId) {

	public CrmOwner {
		if ((leadId == null) == (organisationId == null)) {
			throw new IllegalArgumentException("Exactly one of leadId or organisationId is required");
		}
	}
}
