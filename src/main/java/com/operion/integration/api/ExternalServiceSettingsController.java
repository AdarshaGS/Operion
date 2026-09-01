package com.operion.integration.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.integration.ExternalService;
import com.operion.integration.ExternalServicePropertyCatalog;
import com.operion.integration.ExternalServicePropertyDefinition;
import com.operion.integration.ExternalServiceRepository;
import com.operion.integration.OrganisationExternalServiceProperty;
import com.operion.integration.OrganisationExternalServicePropertyRepository;
import com.operion.integration.OrganisationExternalServicePropertyService;
import com.operion.integration.OrganisationExternalServiceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current organisation's own view of/control over its integrations - always resolves
 * via TenantContext, same convention as OrganisationBrandingController. A service the
 * organisation isn't entitled to still appears (enabled=false) so the UI can show it
 * locked rather than hiding it outright; see PlatformExternalServiceController for how
 * entitlement is granted.
 */
@RestController
@RequestMapping("/api/v1/organisation/external-services")
public class ExternalServiceSettingsController {

	private final ExternalServiceRepository externalServiceRepository;
	private final OrganisationExternalServiceRepository organisationExternalServiceRepository;
	private final OrganisationExternalServicePropertyRepository organisationExternalServicePropertyRepository;
	private final ExternalServicePropertyCatalog propertyCatalog;
	private final OrganisationExternalServicePropertyService organisationExternalServicePropertyService;

	public ExternalServiceSettingsController(ExternalServiceRepository externalServiceRepository,
			OrganisationExternalServiceRepository organisationExternalServiceRepository,
			OrganisationExternalServicePropertyRepository organisationExternalServicePropertyRepository,
			ExternalServicePropertyCatalog propertyCatalog,
			OrganisationExternalServicePropertyService organisationExternalServicePropertyService) {
		this.externalServiceRepository = externalServiceRepository;
		this.organisationExternalServiceRepository = organisationExternalServiceRepository;
		this.organisationExternalServicePropertyRepository = organisationExternalServicePropertyRepository;
		this.propertyCatalog = propertyCatalog;
		this.organisationExternalServicePropertyService = organisationExternalServicePropertyService;
	}

	@GetMapping
	public List<ExternalServiceSettingsResponse> list() {
		Long organisationId = TenantContext.getOrganisationId();
		return externalServiceRepository.findAll().stream().map(service -> toResponse(organisationId, service)).toList();
	}

	@PutMapping("/{serviceKey}/properties")
	@RequirePermission("ORGANISATION_MANAGE")
	public ExternalServiceSettingsResponse updateProperties(@PathVariable String serviceKey,
			@RequestBody UpdateExternalServicePropertiesRequest request) {
		organisationExternalServicePropertyService.updateProperties(serviceKey, request.properties());
		ExternalService service = externalServiceRepository.findByServiceKey(serviceKey)
				.orElseThrow(() -> new IllegalArgumentException("No external service with key " + serviceKey));
		return toResponse(TenantContext.getOrganisationId(), service);
	}

	private ExternalServiceSettingsResponse toResponse(Long organisationId, ExternalService service) {
		boolean enabled = organisationExternalServiceRepository.findByOrganisationIdAndExternalServiceId(organisationId, service.getId())
				.map(entitlement -> entitlement.isEnabled())
				.orElse(false);
		List<ExternalServicePropertyStatusResponse> properties = propertyCatalog.propertiesFor(service.getServiceKey()).stream()
				.map(definition -> toStatus(service, definition))
				.toList();
		return new ExternalServiceSettingsResponse(service.getServiceKey(), service.getDisplayName(), enabled, properties);
	}

	private ExternalServicePropertyStatusResponse toStatus(ExternalService service, ExternalServicePropertyDefinition definition) {
		boolean configured = organisationExternalServicePropertyRepository
				.findByExternalServiceIdAndPropertyKey(service.getId(), definition.key())
				.map(OrganisationExternalServiceProperty::getPropertyValue)
				.map(value -> !value.isBlank())
				.orElse(false);
		return new ExternalServicePropertyStatusResponse(definition.key(), definition.secret(), configured);
	}
}
