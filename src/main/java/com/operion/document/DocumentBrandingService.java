package com.operion.document;

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationBranding;
import com.operion.organisation.OrganisationBrandingRepository;
import com.operion.organisation.OrganisationConfiguration;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.organisation.OrganisationRepository;
import com.operion.storage.AssetStorageService;

/**
 * Resolves the current school's printable identity: the branding override name, else the
 * organisation's own name; the branding address line, else the configured postal address.
 */
@Service
public class DocumentBrandingService {

	private final OrganisationBrandingRepository brandingRepository;
	private final OrganisationConfigurationRepository configurationRepository;
	private final OrganisationRepository organisationRepository;
	private final AssetStorageService assetStorageService;

	public DocumentBrandingService(OrganisationBrandingRepository brandingRepository,
			OrganisationConfigurationRepository configurationRepository, OrganisationRepository organisationRepository,
			AssetStorageService assetStorageService) {
		this.brandingRepository = brandingRepository;
		this.configurationRepository = configurationRepository;
		this.organisationRepository = organisationRepository;
		this.assetStorageService = assetStorageService;
	}

	public DocumentBranding current() {
		Long organisationId = TenantContext.getOrganisationId();
		OrganisationBranding branding = brandingRepository.findById(organisationId).orElse(null);
		OrganisationConfiguration configuration = configurationRepository.findById(organisationId).orElse(null);
		String organisationName = organisationRepository.findById(organisationId).map(Organisation::getName).orElse(null);

		String schoolName = branding != null && notBlank(branding.getSchoolNameOverride()) ? branding.getSchoolNameOverride() : organisationName;
		String address = branding != null && notBlank(branding.getAddressLine()) ? branding.getAddressLine() : postalAddress(configuration);
		String currency = configuration != null && notBlank(configuration.getDefaultCurrency()) ? configuration.getDefaultCurrency() : "INR";
		return new DocumentBranding(schoolName, address,
				branding == null ? null : branding.getAffiliationText(), branding == null ? null : branding.getFooterText(),
				branding == null ? null : branding.getLogoRef(), branding == null ? null : branding.getStampRef(),
				branding == null ? null : branding.getSignatureRef(), currency);
	}

	/** A stored PNG/JPG as a data: URI for embedding; empty for no reference, a missing file, or a non-image. */
	public Optional<String> imageDataUri(String reference) {
		if (reference == null) {
			return Optional.empty();
		}
		String lower = reference.toLowerCase();
		String mime = lower.endsWith(".png") ? "image/png" : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg" : null;
		if (mime == null) {
			return Optional.empty();
		}
		return assetStorageService.read(reference).map(bytes -> "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes));
	}

	private static String postalAddress(OrganisationConfiguration configuration) {
		if (configuration == null) {
			return null;
		}
		String joined = Stream.of(configuration.getAddressLine1(), configuration.getAddressLine2(), configuration.getCity(),
						configuration.getState(), configuration.getPincode())
				.filter(Objects::nonNull)
				.filter(DocumentBrandingService::notBlank)
				.collect(Collectors.joining(", "));
		return joined.isEmpty() ? null : joined;
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}
}
