package com.operion.timetable.api;

import com.operion.authorization.RequirePermission;
import com.operion.common.InvalidRequestException;
import com.operion.common.TenantContext;
import com.operion.timetable.TimetableSettings;
import com.operion.timetable.TimetableSettingsRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET returns in-memory defaults when the organisation hasn't configured anything; PUT upserts the lazy row. */
@RestController
@RequestMapping("/api/v1/timetable/settings")
@RequirePermission("TIMETABLE_VIEW")
public class TimetableSettingsController {

	private final TimetableSettingsRepository settingsRepository;

	public TimetableSettingsController(TimetableSettingsRepository settingsRepository) {
		this.settingsRepository = settingsRepository;
	}

	@GetMapping
	public TimetableSettingsResponse get() {
		return TimetableSettingsResponse.from(settingsRepository.findByOrganisationId(TenantContext.getOrganisationId())
				.orElseGet(TimetableSettings::new));
	}

	@PutMapping
	@RequirePermission("TIMETABLE_MANAGE")
	public TimetableSettingsResponse update(@Valid @RequestBody UpdateTimetableSettingsRequest request) {
		requireAtLeastOneOrNull("The daily teacher workload limit", request.maxTeacherPeriodsPerDay());
		requireAtLeastOneOrNull("The weekly teacher workload limit", request.maxTeacherPeriodsPerWeek());
		if (request.maxTeacherPeriodsPerDay() != null && request.maxTeacherPeriodsPerWeek() != null
				&& request.maxTeacherPeriodsPerDay() > request.maxTeacherPeriodsPerWeek()) {
			throw new InvalidRequestException("The daily teacher workload limit cannot exceed the weekly limit");
		}
		TimetableSettings settings = settingsRepository.findByOrganisationId(TenantContext.getOrganisationId())
				.orElseGet(TimetableSettings::new);
		settings.setGapMinutes(request.gapMinutes());
		settings.setMaxTeacherPeriodsPerDay(request.maxTeacherPeriodsPerDay());
		settings.setMaxTeacherPeriodsPerWeek(request.maxTeacherPeriodsPerWeek());
		return TimetableSettingsResponse.from(settingsRepository.save(settings));
	}

	private static void requireAtLeastOneOrNull(String what, Integer value) {
		if (value != null && value < 1) {
			throw new InvalidRequestException(what + " must be at least 1 (leave it empty for no limit)");
		}
	}
}
