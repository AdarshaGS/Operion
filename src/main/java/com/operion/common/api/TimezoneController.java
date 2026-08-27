package com.operion.common.api;

import java.util.Comparator;
import java.util.List;

import com.operion.common.Timezone;
import com.operion.common.TimezoneRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only global catalog, no @RequirePermission - same trust tier as
 * PermissionController, since this only feeds picker fields (business settings, campus
 * profile) and carries nothing org-specific or sensitive.
 */
@RestController
@RequestMapping("/api/v1/timezones")
public class TimezoneController {

	private final TimezoneRepository timezoneRepository;

	public TimezoneController(TimezoneRepository timezoneRepository) {
		this.timezoneRepository = timezoneRepository;
	}

	@GetMapping
	public List<TimezoneResponse> list() {
		return timezoneRepository.findAll().stream()
				.sorted(Comparator.comparing(Timezone::getName))
				.map(TimezoneResponse::from)
				.toList();
	}
}
