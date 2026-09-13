package com.operion.platform.api;

import com.operion.platform.settings.PlatformSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformSettingsController {

	private final PlatformSettingsService platformSettingsService;

	public PlatformSettingsController(PlatformSettingsService platformSettingsService) {
		this.platformSettingsService = platformSettingsService;
	}

	@GetMapping("/api/v1/platform/settings")
	public PlatformSettingsResponse get() {
		return PlatformSettingsResponse.from(platformSettingsService.current());
	}

	@PutMapping("/api/v1/platform/settings")
	public PlatformSettingsResponse update(@RequestBody UpdatePlatformSettingsRequest request) {
		return PlatformSettingsResponse.from(platformSettingsService.updateTrialDays(request.trialDays()));
	}
}
