package com.operion.student.api;

import com.operion.authorization.RequirePermission;
import com.operion.common.EnumParam;
import com.operion.common.TenantContext;
import com.operion.student.AdmissionFeeClearanceMode;
import com.operion.student.AdmissionSettings;
import com.operion.student.AdmissionSettingsRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET is open to any authenticated org member (a settings-display endpoint) and returns the in-memory
 * default (OFF) rather than 404ing when the org hasn't configured this yet, same convention as
 * ExaminationSettingsController and LibrarySettingsController. PUT upserts, since the row is created
 * lazily on first save, and needs ORGANISATION_MANAGE: the mode switches a financial policy for the
 * whole school on, which is an administrator's call rather than a registrar's.
 */
@RestController
@RequestMapping("/api/v1/admission-settings")
public class AdmissionSettingsController {

	private final AdmissionSettingsRepository admissionSettingsRepository;

	public AdmissionSettingsController(AdmissionSettingsRepository admissionSettingsRepository) {
		this.admissionSettingsRepository = admissionSettingsRepository;
	}

	@GetMapping
	public AdmissionSettingsResponse get() {
		return admissionSettingsRepository.findByOrganisationId(TenantContext.getOrganisationId())
				.map(AdmissionSettingsResponse::from)
				.orElseGet(AdmissionSettingsResponse::defaults);
	}

	@PutMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public AdmissionSettingsResponse update(@Valid @RequestBody UpdateAdmissionSettingsRequest request) {
		AdmissionSettings settings = admissionSettingsRepository.findByOrganisationId(TenantContext.getOrganisationId())
				.orElseGet(AdmissionSettings::new);
		settings.setFeeClearanceMode(EnumParam.parse(AdmissionFeeClearanceMode.class, request.feeClearanceMode()));
		return AdmissionSettingsResponse.from(admissionSettingsRepository.save(settings));
	}
}
