package com.operion.organisation.api;

import java.util.List;

import com.operion.organisation.NewAdminAccount;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.organisation.OrganisationService;
import com.operion.organisation.OrganisationStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal smoke-test surface for the Foundation module - lets the static UI
 * (src/main/resources/static/index.html) exercise provisioning, login and status
 * transitions end to end. Not the final API design (see ai-context/erp-system-plan.md
 * §5). Creation alone is public (bootstrapping); everything else requires the token
 * issued by /api/v1/auth/login, per JwtAuthenticationInterceptor.
 */
@RestController
@RequestMapping("/api/v1/organisations")
public class OrganisationController {

	private final OrganisationService organisationService;
	private final OrganisationRepository organisationRepository;

	public OrganisationController(OrganisationService organisationService, OrganisationRepository organisationRepository) {
		this.organisationService = organisationService;
		this.organisationRepository = organisationRepository;
	}

	@PostMapping
	public OrganisationResponse create(@RequestBody CreateOrganisationRequest request) {
		NewAdminAccount admin = new NewAdminAccount(
				request.adminEmail(), request.adminPassword(), request.adminFirstName(), request.adminLastName());
		Organisation organisation = organisationService.provision(request.name(), request.legalName(), request.slug(), admin);
		return OrganisationResponse.from(organisation);
	}

	@GetMapping
	public List<OrganisationResponse> list() {
		return organisationRepository.findAll().stream().map(OrganisationResponse::from).toList();
	}

	@GetMapping("/{id}")
	public OrganisationResponse get(@PathVariable Long id) {
		return OrganisationResponse.from(findOrThrow(id));
	}

	@PatchMapping("/{id}/status")
	public OrganisationResponse changeStatus(@PathVariable Long id, @RequestBody ChangeOrganisationStatusRequest request) {
		OrganisationStatus target = OrganisationStatus.valueOf(request.status());
		return OrganisationResponse.from(organisationService.changeStatus(id, target));
	}

	private Organisation findOrThrow(Long id) {
		return organisationRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with id " + id));
	}
}
