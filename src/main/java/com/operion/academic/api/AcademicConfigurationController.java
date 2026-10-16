package com.operion.academic.api;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

import com.operion.academic.AcademicConfiguration;
import com.operion.academic.AcademicConfigurationRepository;
import com.operion.authorization.RequirePermission;
import com.operion.common.InvalidRequestException;
import com.operion.common.TenantContext;
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

	public AcademicConfigurationController(AcademicConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	@GetMapping
	public AcademicConfigurationResponse get() {
		return AcademicConfigurationResponse.from(currentConfiguration());
	}

	/** Partial update: a time left out of the body is unchanged, an explicit null (or empty string) clears it. */
	@PatchMapping
	@RequirePermission("ACADEMIC_CONFIGURATION_MANAGE")
	public AcademicConfigurationResponse update(@RequestBody Map<String, String> request) {
		AcademicConfiguration configuration = currentConfiguration();
		LocalTime start = request.containsKey("schoolStartTime") ? parseTime("schoolStartTime", request.get("schoolStartTime")) : configuration.getSchoolStartTime();
		LocalTime end = request.containsKey("schoolEndTime") ? parseTime("schoolEndTime", request.get("schoolEndTime")) : configuration.getSchoolEndTime();
		if (start != null && end != null && !end.isAfter(start)) {
			throw new InvalidRequestException("School end time must be after the start time");
		}
		configuration.setSchoolStartTime(start);
		configuration.setSchoolEndTime(end);
		return AcademicConfigurationResponse.from(configurationRepository.save(configuration));
	}

	private static LocalTime parseTime(String field, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalTime.parse(value);
		} catch (DateTimeParseException ex) {
			throw new InvalidRequestException(field + " must be a time like 08:30");
		}
	}

	private AcademicConfiguration currentConfiguration() {
		Long organisationId = TenantContext.getOrganisationId();
		return configurationRepository.findById(organisationId)
				.orElseGet(() -> configurationRepository.save(new AcademicConfiguration(organisationId)));
	}
}
