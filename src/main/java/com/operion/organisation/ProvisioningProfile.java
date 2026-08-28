package com.operion.organisation;

/**
 * Optional timezone/contact/address input for {@link OrganisationService#provision}, mapped
 * onto the new {@link Organisation}'s {@link OrganisationConfiguration} - the same record
 * {@link com.operion.organisation.api.OrganisationProfileController} edits post-creation.
 */
public record ProvisioningProfile(String timezone, String primaryContactName, String primaryContactEmail,
		String primaryContactPhone, String addressLine1, String addressLine2, String city, String state,
		String country, String pincode) {
}
