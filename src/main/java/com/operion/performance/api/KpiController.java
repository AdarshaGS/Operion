package com.operion.performance.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.EnumParam;
import com.operion.common.TenantContext;
import com.operion.performance.KpiMetric;
import com.operion.performance.KpiService;
import com.operion.performance.KpiSettings;
import com.operion.performance.KpiSettingsRepository;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Compares colleagues, so even reading it is permission-gated; it is never exposed to guardians. */
@RestController
@RequestMapping("/api/v1/performance")
@RequirePermission("KPI_VIEW")
public class KpiController {

	private static final int DEFAULT_PERIOD_DAYS = 30;

	private final KpiService kpiService;
	private final KpiSettingsRepository settingsRepository;

	public KpiController(KpiService kpiService, KpiSettingsRepository settingsRepository) {
		this.kpiService = kpiService;
		this.settingsRepository = settingsRepository;
	}

	@GetMapping("/metrics")
	public List<KpiDefinitionResponse> metrics() {
		return kpiService.definitions().stream().map(KpiDefinitionResponse::from).toList();
	}

	@PutMapping("/metrics/{metric}")
	@RequirePermission("KPI_MANAGE")
	public KpiDefinitionResponse configure(@PathVariable String metric, @Valid @RequestBody ConfigureKpiRequest request) {
		return KpiDefinitionResponse.from(kpiService.configure(EnumParam.parse(KpiMetric.class, metric), request.enabled(),
				request.weight(), request.target(), request.days()));
	}

	@GetMapping("/scorecard")
	public ScorecardResponse scorecard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate end = to != null ? to : LocalDate.now();
		LocalDate start = from != null ? from : end.minusDays(DEFAULT_PERIOD_DAYS - 1);
		return ScorecardResponse.from(kpiService.scorecard(start, end));
	}

	@GetMapping("/settings")
	public KpiSettingsRequest settings() {
		return new KpiSettingsRequest(settingsRepository.findByOrganisationId(TenantContext.getOrganisationId())
				.map(KpiSettings::isTeacherSelfView).orElse(false));
	}

	@PutMapping("/settings")
	@RequirePermission("KPI_MANAGE")
	public KpiSettingsRequest updateSettings(@RequestBody KpiSettingsRequest request) {
		KpiSettings settings = settingsRepository.findByOrganisationId(TenantContext.getOrganisationId()).orElseGet(KpiSettings::new);
		settings.setTeacherSelfView(request.teacherSelfView());
		return new KpiSettingsRequest(settingsRepository.save(settings).isTeacherSelfView());
	}
}
