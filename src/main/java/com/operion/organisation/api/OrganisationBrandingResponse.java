package com.operion.organisation.api;

import com.operion.organisation.OrganisationBranding;
import com.operion.storage.AssetStorageService;

/**
 * Carries both the raw ref and its resolved URL for each asset slot - the frontend
 * displays the URL but must echo the ref back unchanged on PUT for any slot it isn't
 * replacing (see UpdateOrganisationBrandingRequest), and a URL alone can't be reversed
 * back into one without the frontend assuming resolveUrl()'s path shape.
 */
public record OrganisationBrandingResponse(String logoRef, String logoUrl, String stampRef, String stampUrl, String signatureRef,
		String signatureUrl, String schoolNameOverride, String addressLine, String affiliationText) {

	public static OrganisationBrandingResponse from(OrganisationBranding branding, AssetStorageService assetStorageService) {
		return new OrganisationBrandingResponse(branding.getLogoRef(), resolve(branding.getLogoRef(), assetStorageService),
				branding.getStampRef(), resolve(branding.getStampRef(), assetStorageService), branding.getSignatureRef(),
				resolve(branding.getSignatureRef(), assetStorageService), branding.getSchoolNameOverride(), branding.getAddressLine(),
				branding.getAffiliationText());
	}

	private static String resolve(String reference, AssetStorageService assetStorageService) {
		return reference == null ? null : assetStorageService.resolveUrl(reference);
	}
}
