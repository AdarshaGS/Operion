package com.operion.academic.api;

import java.time.LocalTime;

import com.operion.academic.AcademicConfiguration;
import com.operion.academic.AcademicConfigurationRepository;
import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * School-vertical settings (school start/end time), split from the core
 * {@link com.operion.organisation.api.OrganisationConfigurationController} per #75/#143 -
 * same "always resolve the caller's own org from TenantContext" reasoning applies here too.
 * Get-or-create rather than provisioned eagerly by OrganisationService (core), since core
 * may never import a vertical package - see ai-context/platform-boundaries.md.
 */
@RestController
@RequestMapping("/api/v1/academic/settings")
@RequirePermission("ACADEMIC_CONFIGURATION_VIEW")
public class AcademicConfigurationController {

	private final AcademicConfigurationRepository configurationRepository;
	private final OrganisationRepository organisationRepository;

	public AcademicConfigurationController(AcademicConfigurationRepository configurationRepository,
			OrganisationRepository organisationRepository) {
		this.configurationRepository = configurationRepository;
		this.organisationRepository = organisationRepository;
	}

	@GetMapping
	public AcademicConfigurationResponse get() {
		return AcademicConfigurationResponse.from(currentConfiguration());
	}

	@PatchMapping
	@RequirePermission("ACADEMIC_CONFIGURATION_MANAGE")
	public AcademicConfigurationResponse update(@RequestBody UpdateAcademicConfigurationRequest request) {
		AcademicConfiguration configuration = currentConfiguration();
		configuration.setSchoolStartTime(request.schoolStartTime() != null ? LocalTime.parse(request.schoolStartTime()) : null);
		configuration.setSchoolEndTime(request.schoolEndTime() != null ? LocalTime.parse(request.schoolEndTime()) : null);
		return AcademicConfigurationResponse.from(configurationRepository.save(configuration));
	}

	private AcademicConfiguration currentConfiguration() {
		Long organisationId = TenantContext.getOrganisationId();
		return configurationRepository.findById(organisationId).orElseGet(() -> {
			Organisation organisation = organisationRepository.findById(organisationId)
					.orElseThrow(() -> new IllegalArgumentException("No organisation with id " + organisationId));
			return configurationRepository.save(new AcademicConfiguration(organisation));
		});
	}
}
