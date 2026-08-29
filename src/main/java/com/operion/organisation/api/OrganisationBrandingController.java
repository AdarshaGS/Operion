package com.operion.organisation.api;

import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.organisation.OrganisationBranding;
import com.operion.organisation.OrganisationBrandingRepository;
import com.operion.storage.AssetStorageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Always resolves the caller's own org from TenantContext, same reasoning as
 * OrganisationConfigurationController. A branding row exists for every org from
 * provisioning onward (see OrganisationService.provision), so no get-or-create branch
 * is needed here.
 */
@RestController
@RequestMapping("/api/v1/organisation/branding")
public class OrganisationBrandingController {

	private final OrganisationBrandingRepository brandingRepository;
	private final AssetStorageService assetStorageService;

	public OrganisationBrandingController(OrganisationBrandingRepository brandingRepository, AssetStorageService assetStorageService) {
		this.brandingRepository = brandingRepository;
		this.assetStorageService = assetStorageService;
	}

	@GetMapping
	public OrganisationBrandingResponse get() {
		return OrganisationBrandingResponse.from(currentBranding(), assetStorageService);
	}

	@PutMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public OrganisationBrandingResponse update(@Valid @RequestBody UpdateOrganisationBrandingRequest request) {
		OrganisationBranding branding = currentBranding();
		branding.setLogoRef(request.logoRef());
		branding.setStampRef(request.stampRef());
		branding.setSignatureRef(request.signatureRef());
		branding.setSchoolNameOverride(request.schoolNameOverride());
		branding.setAddressLine(request.addressLine());
		branding.setAffiliationText(request.affiliationText());
		branding.setFooterText(request.footerText());
		branding.setAdmissionNumberFormat(request.admissionNumberFormat());
		branding.setInvoiceNumberFormat(request.invoiceNumberFormat());
		branding.setReceiptNumberFormat(request.receiptNumberFormat());
		return OrganisationBrandingResponse.from(brandingRepository.save(branding), assetStorageService);
	}

	private OrganisationBranding currentBranding() {
		Long organisationId = TenantContext.getOrganisationId();
		return brandingRepository.findById(organisationId)
				.orElseThrow(() -> new IllegalArgumentException("No branding record for organisation " + organisationId));
	}
}
