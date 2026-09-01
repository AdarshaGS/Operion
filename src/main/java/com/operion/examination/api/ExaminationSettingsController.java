package com.operion.examination.api;

import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.examination.ExaminationSettings;
import com.operion.examination.ExaminationSettingsRepository;
import com.operion.examination.PassFailStrategy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET is open to any authenticated org member (a settings-display endpoint, same
 * reasoning as DocumentTemplateController.get()) and returns in-memory defaults rather
 * than 404ing when the org hasn't configured this yet. PUT upserts, since the row is
 * created lazily on first save. Per #135/#136.
 */
@RestController
@RequestMapping("/api/v1/examinations/settings")
public class ExaminationSettingsController {

	private final ExaminationSettingsRepository examinationSettingsRepository;

	public ExaminationSettingsController(ExaminationSettingsRepository examinationSettingsRepository) {
		this.examinationSettingsRepository = examinationSettingsRepository;
	}

	@GetMapping
	public ExaminationSettingsResponse get() {
		return examinationSettingsRepository.findByOrganisationId(TenantContext.getOrganisationId())
				.map(ExaminationSettingsResponse::from)
				.orElseGet(ExaminationSettingsResponse::defaults);
	}

	@PutMapping
	@RequirePermission("EXAM_MANAGE")
	public ExaminationSettingsResponse update(@RequestBody UpdateExaminationSettingsRequest request) {
		ExaminationSettings settings = examinationSettingsRepository.findByOrganisationId(TenantContext.getOrganisationId())
				.orElseGet(ExaminationSettings::new);
		settings.setRankingEnabled(request.rankingEnabled());
		settings.setPassFailStrategy(PassFailStrategy.valueOf(request.passFailStrategy()));
		settings.setMinimumAggregatePercentage(request.minimumAggregatePercentage());
		return ExaminationSettingsResponse.from(examinationSettingsRepository.save(settings));
	}
}
