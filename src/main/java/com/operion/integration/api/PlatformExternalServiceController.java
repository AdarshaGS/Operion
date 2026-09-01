package com.operion.integration.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.operion.integration.ExternalService;
import com.operion.integration.ExternalServiceRepository;
import com.operion.integration.OrganisationExternalServiceAdminService;
import com.operion.integration.OrganisationExternalServiceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a platform admin grant/revoke one organisation's entitlement to configure a given
 * integration - mounted under /api/v1/platform/**, gated by
 * PlatformAuthenticationInterceptor only (no granular permission catalog on this plane
 * yet, see PlatformPermissions). Never exposes an organisation's actual credential
 * values - see OrganisationExternalServiceProperty's doc comment for why this plane
 * structurally can't.
 */
@RestController
@RequestMapping("/api/v1/platform/organisations/{organisationId}/external-services")
public class PlatformExternalServiceController {

	private final ExternalServiceRepository externalServiceRepository;
	private final OrganisationExternalServiceRepository organisationExternalServiceRepository;
	private final OrganisationExternalServiceAdminService organisationExternalServiceAdminService;

	public PlatformExternalServiceController(ExternalServiceRepository externalServiceRepository,
			OrganisationExternalServiceRepository organisationExternalServiceRepository,
			OrganisationExternalServiceAdminService organisationExternalServiceAdminService) {
		this.externalServiceRepository = externalServiceRepository;
		this.organisationExternalServiceRepository = organisationExternalServiceRepository;
		this.organisationExternalServiceAdminService = organisationExternalServiceAdminService;
	}

	@GetMapping
	public List<OrganisationExternalServiceResponse> list(@PathVariable Long organisationId) {
		Map<Long, Boolean> enabledByServiceId = organisationExternalServiceRepository.findByOrganisationId(organisationId).stream()
				.collect(Collectors.toMap(row -> row.getExternalService().getId(), row -> row.isEnabled()));
		return externalServiceRepository.findAll().stream()
				.map(service -> OrganisationExternalServiceResponse.of(service, enabledByServiceId.getOrDefault(service.getId(), false)))
				.toList();
	}

	@PatchMapping("/{serviceKey}")
	public OrganisationExternalServiceResponse setEnabled(@PathVariable Long organisationId, @PathVariable String serviceKey,
			@RequestBody SetExternalServiceEnabledRequest request) {
		ExternalService service = organisationExternalServiceAdminService.setEnabled(organisationId, serviceKey, request.enabled())
				.getExternalService();
		return OrganisationExternalServiceResponse.of(service, request.enabled());
	}
}
